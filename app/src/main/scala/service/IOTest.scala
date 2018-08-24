package service
import java.io.{ByteArrayInputStream, InputStream}

import cats.effect.IO
import com.twitter.concurrent.AsyncStream

import scala.concurrent.ExecutionContext.Implicits.global

object IOTest {

  def main(args: Array[String]): Unit = {
    val is: InputStream = new ByteArrayInputStream("test".getBytes)
    val stream: fs2.Stream[IO, Byte] = fs2.io.readInputStream(IO(is), 128)

    val is2: InputStream = stream.through(fs2.io.toInputStream).compile.toList.map(_.head).unsafeRunSync()

    println(scala.io.Source.fromInputStream(is2).mkString)
    AsyncStream
  }

}
