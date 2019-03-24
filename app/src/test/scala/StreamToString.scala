import java.nio.charset.StandardCharsets

import TestInterpreters.TestProgram
import fs2.Stream
import cats.implicits._

object StreamToString {

  def streamToString(stream: Stream[TestProgram, Byte]): String = {
    val byteArray = stream.compile.toList.run.unsafeRunSync._2.toArray
    new String(byteArray, StandardCharsets.UTF_8)
  }

}
