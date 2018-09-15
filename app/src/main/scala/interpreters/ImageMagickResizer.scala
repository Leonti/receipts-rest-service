package interpreters
import java.io.File

import algebras.ImageResizeAlg
import cats.effect.IO
import scala.sys.process._

class ImageMagickResizer extends ImageResizeAlg[IO] {

  override def resizeToPixelSize(originalFile: File, pixels: Long): IO[File] = IO {
    val resized = new File(originalFile.getAbsolutePath + s"_resized_$pixels")
    val cmd     = s"convert ${originalFile.getAbsolutePath} -resize $pixels@> ${resized.getAbsolutePath}"

    if (cmd.! != 0) {
      throw new RuntimeException(s"Could not resize the image ${originalFile.getAbsolutePath}")
    }
    resized
  }

  override def resizeToFileSize(originalFile: File, sizeInMb: Double): IO[File] = IO {
    val resized = new File(originalFile.getAbsolutePath + s"_resized_$sizeInMb")
    val cmd     = s"convert ${originalFile.getAbsolutePath} -define jpeg:extent=${sizeInMb}MB ${resized.getAbsolutePath}"

    if (cmd.! != 0) {
      throw new RuntimeException(s"Could not resize the image ${originalFile.getAbsolutePath}")
    }
    resized
  }
}
