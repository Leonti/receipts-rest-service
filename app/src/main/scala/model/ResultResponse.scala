package model
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class ErrorResponse(error: String)

case object ErrorResponse {
  implicit val errorResponseDecoder: Decoder[ErrorResponse] = deriveDecoder
  implicit val errorResponseEncoder: Encoder[ErrorResponse] = deriveEncoder
}

case class OkResponse(message: String)
