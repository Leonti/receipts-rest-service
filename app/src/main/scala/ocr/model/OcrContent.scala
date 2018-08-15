package ocr.model
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class OcrContent(content: String)

object OcrContent {
  implicit val ocrContentDecoder: Decoder[OcrContent] = deriveDecoder
  implicit val ocrContentEncoder: Encoder[OcrContent] = deriveEncoder
}
