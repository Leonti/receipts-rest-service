package service

import java.io.File
import java.util.concurrent.Executors

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import spray.json.{DeserializationException, JsString, JsValue, RootJsonFormat}

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process._

sealed trait ImageSize { def pixels: Int }
case object WebSize extends ImageSize { val pixels: Int = 1000000 }

object ImageSizeFormat extends RootJsonFormat[ImageSize] {

  val jsMappings: Map[String, ImageSize] = Map("WEB_SIZE" -> WebSize)

  private def asString(imageSize: ImageSize): String = jsMappings.filter(pair => pair._2 == imageSize).head._1

  def write(imageSize: ImageSize) = JsString(asString(imageSize))

  def read(value: JsValue): ImageSize =
    value match {
      case JsString(asString) => jsMappings(asString)
      case _                  => throw new DeserializationException("ImageSize should be encoded as JsString!")
    }
}

class ImageResizingService {
  val logger      = Logger(LoggerFactory.getLogger("ImageResizingService"))
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  val resize: (File, ImageSize) => Future[File] = (originalFile, imageSize: ImageSize) => {
    val resized = new File(originalFile.getAbsolutePath + s"_resized_${imageSize.pixels}")
    val cmd     = s"convert ${originalFile.getAbsolutePath} -resize ${imageSize.pixels}@> ${resized.getAbsolutePath}"

    Future {

      if (cmd.! != 0) {
        logger.error("Could not resize image:", cmd)
        throw new RuntimeException(s"Could not resize the image ${originalFile.getAbsolutePath}")
      }

      resized
    }
  }

  val resizeToSize: (File, Double) => Future[File] = (originalFile, sizeInMb) => {
    // convert original.jpeg -define jpeg:extent=300kb output.jpg

    val resized = new File(originalFile.getAbsolutePath + s"_resized_${sizeInMb}")
    val cmd     = s"convert ${originalFile.getAbsolutePath} -define jpeg:extent=${sizeInMb}MB ${resized.getAbsolutePath}"

    Future {

      if (cmd.! != 0) {
        logger.error("Could not resize image:", cmd)
        throw new RuntimeException(s"Could not resize the image ${originalFile.getAbsolutePath}")
      }

      resized
    }
  }
}
