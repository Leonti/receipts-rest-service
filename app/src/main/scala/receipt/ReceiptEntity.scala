package receipt

import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import model.{Serialization, WithId}

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

  implicit object ReceiptEntityBSONReader extends BSONDocumentReader[ReceiptEntity] {

    def read(doc: BSONDocument): ReceiptEntity =
      Serialization.deserialize(
        doc,
        ReceiptEntity(
          id = doc.getAs[String]("_id").get,
          userId = doc.getAs[String]("userId").get,
          files = doc.getAs[List[FileEntity]]("files").get,
          description = doc.getAs[String]("description").get,
          total = doc.getAs[String]("total") match {
            case Some(value) => if (value != "None") Some(BigDecimal(value.replace("Some(", "").replace(")", ""))) else None
            case None        => None
          },
          timestamp = doc.getAs[Long]("timestamp").get,
          lastModified = doc.getAs[Long]("lastModified").get,
          transactionTime = doc.getAs[Long]("transactionTime").get,
          tags = doc.getAs[List[String]]("tags").getOrElse(List.empty)
        )
      )
  }

  implicit object ReceiptEntityBSONWriter extends BSONDocumentWriter[ReceiptEntity] {

    def write(receiptEntity: ReceiptEntity): BSONDocument = {
      BSONDocument(
        "_id"             -> receiptEntity.id,
        "userId"          -> receiptEntity.userId,
        "files"           -> receiptEntity.files,
        "description"     -> receiptEntity.description,
        "total"           -> receiptEntity.total.toString,
        "timestamp"       -> receiptEntity.timestamp,
        "lastModified"    -> receiptEntity.lastModified,
        "transactionTime" -> receiptEntity.transactionTime,
        "tags"            -> receiptEntity.tags
      )
    }
  }

}
