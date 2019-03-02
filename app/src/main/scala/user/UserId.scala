package user
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class UserId(value: String) extends AnyVal

object UserId {
  implicit val userIdDecoder: Decoder[UserId] = deriveDecoder
  implicit val userIdEncoder: Encoder[UserId] = deriveEncoder
}
