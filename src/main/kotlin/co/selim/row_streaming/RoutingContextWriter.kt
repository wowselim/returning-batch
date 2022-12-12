package co.selim.row_streaming

import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.RoutingContext
import java.io.Writer

class RoutingContextWriter(
  private val ctx: RoutingContext
) : Writer() {

  override fun close() {
    ctx.response().end()
  }

  override fun flush() = Unit

  override fun write(cbuf: CharArray, off: Int, len: Int) {
    ctx.response().write(Buffer.buffer(cbuf.concatToString(off, off + len)))
  }
}
