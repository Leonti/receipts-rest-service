package user

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class UserInfo(id: String, userName: String)

object UserInfo {
  implicit val userInfoDecoder: Decoder[UserInfo] = deriveDecoder
  implicit val userInfoEncoder: Encoder[UserInfo] = deriveEncoder
}
