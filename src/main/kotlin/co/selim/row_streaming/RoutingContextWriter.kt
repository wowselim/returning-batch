package co.selim.row_streaming

import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.RoutingContext
import java.io.Writer

class RoutingContextWriter(
  private val ctx: RoutingContext
) : Writer() {

  private var buffer: Buffer? = null

  override fun close() {
    if (buffer != null) {
      ctx.response().end(buffer)
      buffer = null
    } else {
      ctx.response().end()
    }
  }

  override fun flush() {
    buffer?.let {
      ctx.response().write(it)
      buffer = null
    }
  }

  override fun write(cbuf: CharArray, off: Int, len: Int) {
    buffer = Buffer.buffer(cbuf.concatToString(off, off + len))
  }
}
