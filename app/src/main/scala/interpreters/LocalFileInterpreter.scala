package interpreters
import java.io.{File, FileInputStream, FileOutputStream, InputStream}
import java.nio.file.{Files, StandardCopyOption}
import java.security.{DigestInputStream, MessageDigest}

import algebras.LocalFileAlg
import cats.effect.IO
import com.twitter.io.Buf
import model.{FileMetaData, GenericMetaData, ImageMetaData}
import util.SimpleImageInfo

import scala.util.Try

class LocalFileInterpreter extends LocalFileAlg[IO] {

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

  override def bufToFile(src: Buf, dst: File): IO[Unit] = src match {
    case Buf.ByteArray.Owned(bytes, _, _) => IO(new FileOutputStream(dst)).bracket(fw => IO(fw.write(bytes)))(fw => IO(fw.close()))
    case _                                => IO.raiseError(new Exception("Buffer type is not supported"))
  }

  override def streamToFile(source: InputStream, file: File): IO[File] =
    IO(source).bracket { in =>
      IO {
        java.nio.file.Files.copy(in, file.toPath, StandardCopyOption.REPLACE_EXISTING)
        file
      }
    }(in => IO(in.close()))

  override def removeFile(file: File): IO[Unit] = IO { file.delete }.map(_ => ())
}
