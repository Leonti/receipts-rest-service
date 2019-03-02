package ocr

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class OcrSearchResult(ids: Seq[String])

object OcrSearchResult {
  implicit val ocrSearchResultDecoder: Decoder[OcrSearchResult] = deriveDecoder
  implicit val ocrSearchResultEncoder: Encoder[OcrSearchResult] = deriveEncoder
}
