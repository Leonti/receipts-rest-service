package pending

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import model.WithId

case class PendingFile(id: String, userId: String, receiptId: String) extends WithId

object PendingFile {

  implicit val pendingFileDecoder: Decoder[PendingFile] = deriveDecoder
  implicit val pendingFileEncoder: Encoder[PendingFile] = deriveEncoder

}
