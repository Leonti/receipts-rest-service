package service
import java.io._

import cats.effect.IO

//import scala.concurrent.Future
//import com.twitter.concurrent.AsyncStream

import scala.concurrent.ExecutionContext.Implicits.global

object IOTest {

  def main(args: Array[String]): Unit = {
    val is: InputStream = new ByteArrayInputStream("test".getBytes)
    val stream: fs2.Stream[IO, Byte] = fs2.io.readInputStream(IO(is), 128)

    val inputStream  = new PipedInputStream()
    val outputStream: OutputStream = new PipedOutputStream(inputStream)

    val sink = fs2.io.writeOutputStreamAsync(IO(outputStream))

    val stream2: fs2.Stream[IO, Byte] = fs2.Stream.repeatEval(IO("d".getBytes()(0))).take(10)

    stream2.to(sink).compile.drain.unsafeRunAsync(e => println(e))

    //val is2: InputStream = stream.through(fs2.io.toInputStream).compile.toList.map(_.head).unsafeRunSync()

    println("Running input stream")
    while (inputStream.read() != -1) {
      println(s"read something")
    }

  //  println(scala.io.Source.fromInputStream(inputStream).mkString)
  }

}
