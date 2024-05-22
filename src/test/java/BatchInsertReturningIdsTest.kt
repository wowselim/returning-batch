import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.coroutines.awaitBlocking
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.PostgreSQLContainer

@ExtendWith(VertxExtension::class)
class BatchInsertReturningIdsTest {

  private val pgContainer = PostgreSQLContainer("postgres:14.3")

  private lateinit var pgPool: Pool

  @BeforeEach
  fun setup(vertx: Vertx): Unit = runBlocking(vertx.dispatcher()) {

    awaitBlocking { pgContainer.start() }
    pgPool = with(pgContainer) {
      val connectOptions = PgConnectOptions.fromUri(jdbcUrl.removePrefix("jdbc:"))
        .setUser(username)
        .setPassword(password)
      val poolOptions = PoolOptions().setMaxSize(4)
      Pool.pool(vertx, connectOptions, poolOptions)
    }

    pgPool.query("create table person(id bigserial primary key, name text)")
      .execute()
      .coAwait()
  }

  @AfterEach
  fun teardown(vertx: Vertx) = runBlocking(vertx.dispatcher()) {
    awaitBlocking { pgContainer.stop() }
  }


  @Test
  fun testBatchInsertReturningIds(vertx: Vertx) = runBlocking(vertx.dispatcher()) {

    val batch = listOf(
      Tuple.of("John"),
      Tuple.of("Jane"),
      Tuple.of("Selim"),
    )
    var rows = pgPool.preparedQuery("insert into person(name) values($1) returning id")
      .executeBatch(batch)
      .coAwait()

    val ids = buildList<Long>(batch.size) {
      do {
        val key = rows.iterator().next().getLong("id");
        add(key)
        rows = rows.next()
      } while (rows != null)
    }

    assertEquals(3, ids.size)
    assertIterableEquals(listOf<Long>(1, 2, 3), ids)
  }

  @Test
  fun testBatchInsertReturningIdsInTransaction(vertx: Vertx) = runBlocking(vertx.dispatcher()) {

    val batch = listOf(
      Tuple.of("John"),
      Tuple.of("Jane"),
      Tuple.of("Selim"),
    )

    var rows = pgPool.withTransaction {
      pgPool.preparedQuery("insert into person(name) values($1) returning id")
        .executeBatch(batch)
    }.coAwait()
    val ids = buildList<Long>(batch.size) {
      do {
        val key = rows.iterator().next().getLong("id");
        add(key)
        rows = rows.next()
      } while (rows != null)
    }

    assertEquals(3, ids.size)
    assertIterableEquals(listOf<Long>(1, 2, 3), ids)
  }
}
