package interpreters
import java.io.File

import algebras.ImageAlg
import cats.effect.IO
import receipt.ImageMetaData

import scala.sys.process._
import io.circe.generic.auto._
import io.circe.parser.decode

case class Geometry(width: Int, height: Int)
case class ImageInfo(geometry: Geometry)
case class ImageResult(image: ImageInfo)

class ImageInt extends ImageAlg[IO] {

  override def resizeToPixelSize(originalFile: File, pixels: Long): IO[File] = IO {
    val resized = new File(originalFile.getAbsolutePath + s"_resized_$pixels")
    val cmd     = s"convert -auto-orient ${originalFile.getAbsolutePath} -resize $pixels@> ${resized.getAbsolutePath}"

    if (cmd.! != 0) {
      throw new RuntimeException(s"Could not resize the image ${originalFile.getAbsolutePath}")
    }
    resized
  }

  override def resizeToFileSize(originalFile: File, sizeInMb: Double): IO[File] = IO {
    val resized = new File(originalFile.getAbsolutePath + s"_resized_$sizeInMb")
    val cmd     = s"convert -auto-orient ${originalFile.getAbsolutePath} -define jpeg:extent=${sizeInMb}MB ${resized.getAbsolutePath}"

    if (cmd.! != 0) {
      throw new RuntimeException(s"Could not resize the image ${originalFile.getAbsolutePath}")
    }
    resized
  }

  override def isImage(file: File): IO[Boolean] = IO {
    val cmd = s"convert ${file.getAbsolutePath} /dev/null"
    cmd.! == 0
  }

  override def getImageMetaData(file: File): IO[ImageMetaData] =
    IO {
      val cmd = s"convert -auto-orient ${file.getAbsolutePath} json:"
      cmd.!!
    } flatMap { output =>
      decode[List[ImageResult]](output) match {
        case Right(imageResults) =>
          IO(
            ImageMetaData(length = file.length,
                          width = imageResults.head.image.geometry.width,
                          height = imageResults.head.image.geometry.height))
        case Left(e) => IO.raiseError(e.fillInStackTrace())
      }
    }
}
