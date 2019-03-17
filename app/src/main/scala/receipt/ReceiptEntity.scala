package receipt

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import model.WithId

case class ReceiptEntity(
    id: String,
    userId: String,
    files: List[FileEntity] = List.empty,
    description: String = "",
    total: Option[BigDecimal] = None,
    timestamp: Long = System.currentTimeMillis,
    lastModified: Long = System.currentTimeMillis(),
    transactionTime: Long = System.currentTimeMillis(),
    tags: List[String] = List.empty
) extends WithId

object ReceiptEntity {

  implicit val receiptEntityDecoder: Decoder[ReceiptEntity] = deriveDecoder
  implicit val receiptEntityEncoder: Encoder[ReceiptEntity] = deriveEncoder

}
