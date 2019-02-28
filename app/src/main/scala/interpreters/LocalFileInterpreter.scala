package interpreters
import java.io.{File, FileInputStream}
import java.nio.file.Files
import java.security.{DigestInputStream, MessageDigest}

import algebras.LocalFileAlg
import fs2.{Stream, io}
import cats.effect.{ContextShift, IO}
import model.{FileMetaData, GenericMetaData, ImageMetaData}
import util.SimpleImageInfo

import scala.concurrent.ExecutionContext
import scala.util.Try

class LocalFileInterpreter(bec: ExecutionContext) extends LocalFileAlg[IO] {
  private implicit val cs: ContextShift[IO] = IO.contextShift(bec)

  override def getFileMetaData(file: File): IO[FileMetaData] = IO {
    val image: Option[SimpleImageInfo] = Try {
      Some(new SimpleImageInfo(file))
    } getOrElse None

    image
      .map(i => ImageMetaData(length = file.length, width = i.getWidth, height = i.getHeight))
      .getOrElse(GenericMetaData(length = file.length))
  }

  override def getMd5(file: File): IO[String] =
    for {
      md5 <- IO(MessageDigest.getInstance("MD5"))
      result <- IO(new DigestInputStream(new FileInputStream(file), md5))
        .bracket(dis =>
          IO {
            val buffer = new Array[Byte](8192)
            while (dis.read(buffer) != -1) {}
        })(dis => IO(dis.close()))
        .map(_ => md5.digest.map("%02x".format(_)).mkString)
    } yield result

  override def moveFile(src: File, dst: File): IO[Unit] = IO(Files.move(src.toPath, dst.toPath)).map(_ => ())

  override def streamToFile(source: Stream[IO, Byte], file: File): IO[File] =
    source
      .through(io.file.writeAll[IO](file.toPath, bec))
      .compile
      .drain
      .map(_ => file)

  override def removeFile(file: File): IO[Unit] = IO { file.delete }.map(_ => ())
}
