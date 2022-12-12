package co.selim.row_streaming

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import io.github.serpro69.kfaker.faker
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitBlocking
import io.vertx.kotlin.coroutines.toReceiveChannel
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.SqlConnection
import io.vertx.sqlclient.Transaction
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer


class MainVerticle : CoroutineVerticle() {

  companion object {
    val logger: Logger = LoggerFactory.getLogger(MainVerticle::class.java)
    val pgContainer = PostgreSQLContainer("postgres:14.3")
  }

  override suspend fun start() {
    awaitBlocking { pgContainer.start() }

    val pgPool = createPgPool()
    seedDatabase(pgPool)

    val router = Router.router(vertx)

    router.get()
      .coroutineHandler { ctx -> dumpTable(pgPool, ctx) }

    val server = vertx
      .createHttpServer()
      .requestHandler(router)
      .listen(8888)
      .await()

    logger.info("Listening on http://localhost:${server.actualPort()}")
  }

  private fun createPgPool(): PgPool {
    return with(pgContainer) {
      val connectOptions = PgConnectOptions.fromUri(jdbcUrl.removePrefix("jdbc:"))
        .setUser(username)
        .setPassword(password)
      val poolOptions = PoolOptions().setMaxSize(4)
      PgPool.pool(vertx, connectOptions, poolOptions)
    }
  }

  private suspend fun seedDatabase(pgPool: PgPool) {
    pgPool.query("create table person(id bigint primary key, name text, address text, phone text)")
      .execute()
      .await()

    val faker = faker { }
    val tuples = awaitBlocking {
      (0 until 50_000).map { i ->
        Tuple.of(i, faker.name.name(), faker.address.fullAddress(), faker.phoneNumber.phoneNumber())
      }
    }
    pgPool.preparedQuery("insert into person values($1, $2, $3, $4)")
      .executeBatch(tuples)
      .await()
  }

  private suspend fun dumpTable(pgPool: PgPool, ctx: RoutingContext) {
    pgPool.withSuspendingTransaction { tx ->
      val rowChannel = prepare("select * from person")
        .await()
        .createStream(50)
        .toReceiveChannel(vertx)

      ctx.response().isChunked = true
      ctx.attachment("person_dump.json")

      JsonFactory().createGenerator(RoutingContextWriter(ctx))
        .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, true)
        .setCodec(DatabindCodec.mapper())
        .use { generator ->
          generator.writeStartArray()

          rowChannel.consume {
            for (row in this) {
              generator.writePOJO(row.toJson())
            }
          }
          tx.commit().await()

          generator.writeEndArray()
        }
    }
  }

  override suspend fun stop() {
    awaitBlocking { pgContainer.stop() }
  }

  private fun Route.coroutineHandler(
    block: suspend (RoutingContext) -> Unit
  ): Route = handler { ctx ->
    launch {
      try {
        block(ctx)
      } catch (t: Throwable) {
        ctx.fail(t)
      }
    }
  }

  private suspend fun <T : Any?> PgPool.withSuspendingTransaction(
    block: suspend SqlConnection.(Transaction) -> T
  ): T {
    return withConnection { connection ->
      Future.future { promise ->
        launch {
          val transaction = connection.begin().await()
          try {
            promise.complete(block(connection, transaction))
          } catch (t: Throwable) {
            promise.fail(t)
            transaction.rollback().await()
          }
        }
      }
    }.await()
  }
}

suspend fun main() {
  Vertx.vertx().deployVerticle(MainVerticle()).await()
}
