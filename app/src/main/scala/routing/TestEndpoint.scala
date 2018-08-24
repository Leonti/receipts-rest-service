package routing

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.Charset

import cats.effect.IO
import io.finch._
import io.finch.syntax._
import com.twitter.conversions.storage._
import com.twitter.concurrent.AsyncStream
import com.twitter.io.{Buf, Reader}
import com.twitter.util.Await

class TestEndpoint {

  val file: Endpoint[AsyncStream[Buf]] = get("stream-of-file") {
    val is: InputStream = new ByteArrayInputStream("test".getBytes)

    val stream: fs2.Stream[IO, Byte] = fs2.io.readInputStream(IO(is), 128)

    val reader = Reader.fromStream(is)

    val async = AsyncStream.fromReader(reader, chunkSize = 128.kilobytes.inBytes.toInt)
    val result = async.toSeq().map(_.fold(Buf.Empty)((acc, a) => acc.concat(a))).map(buf => Buf.decodeString(buf, Charset.forName("UTF-8")))

    val content = Await.result(result)

    println(s"Content '$content'")

    Ok(AsyncStream.fromReader(reader, chunkSize = 128.kilobytes.inBytes.toInt)).withHeader("Content-Type", "application/octet-stream")
  }

}
