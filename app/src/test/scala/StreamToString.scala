import java.nio.charset.StandardCharsets

import cats.effect.IO
import fs2.Stream
object StreamToString {

  def streamToString(stream: Stream[IO, Byte]): String = new String(stream.compile.toList.unsafeRunSync.toArray, StandardCharsets.UTF_8)

}
