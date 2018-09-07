package model
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class AppConfig(googleClientId: String)

object AppConfig {
  implicit val appConfigDecoder: Decoder[AppConfig] = deriveDecoder
  implicit val appConfigEncoder: Encoder[AppConfig] = deriveEncoder
}

case class ExternalUserInfo(email: String, sub: String)
case class AccessToken(value: String)

case class UserId(value: String)

case class RemoteFileId(userId: UserId, fileId: String)

case class FileMeta(md5: String, size: Long)
