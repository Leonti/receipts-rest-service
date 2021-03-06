package model
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class PublicAppConfig(googleClientId: String)

object PublicAppConfig {
  implicit val appConfigDecoder: Decoder[PublicAppConfig] = deriveDecoder
  implicit val appConfigEncoder: Encoder[PublicAppConfig] = deriveEncoder
}

case class ExternalUserInfo(email: String, sub: String)
case class AccessToken(value: String)

sealed trait ImageSize { def pixels: Int }
case object WebSize extends ImageSize { val pixels: Int = 1000000 }

object ImageSizeJson {
  import cats.syntax.either._

  val jsMappings: Map[String, ImageSize] = Map("WEB_SIZE" -> WebSize)

  private def asString(imageSize: ImageSize): String = jsMappings.filter(pair => pair._2 == imageSize).head._1

  implicit val encodeImageSize: Encoder[ImageSize] = Encoder.encodeString.contramap[ImageSize](asString)

  implicit val decodeImageSize: Decoder[ImageSize] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(jsMappings(str)).leftMap(_ => "ImageSize")
  }
}
