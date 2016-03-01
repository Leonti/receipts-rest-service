package model

import reactivemongo.bson.{BSONDocumentWriter, BSONDocument, BSONDocumentReader}

case class ReceiptEntity(
                          id: String = java.util.UUID.randomUUID.toString,
                          userId: String,
                          files: List[String] = List.empty,
                          description: String = "",
                          total: Option[BigDecimal] = None,
                          timestamp: Long = System.currentTimeMillis
                        ) extends WithId

case class FileEntry(id: String)

object ReceiptEntity {

  implicit object ReceiptEntityBSONReader extends BSONDocumentReader[ReceiptEntity] {

    def read(doc: BSONDocument): ReceiptEntity = Serialization.deserialize(doc, ReceiptEntity(
      id = doc.getAs[String]("_id").get,
      userId = doc.getAs[String]("userId"). get,
      files = doc.getAs[List[String]]("files").get,
      description = doc.getAs[String]("description").get,
      total = doc.getAs[String]("total") match {
        case Some(value) => if (value != "None") Some(BigDecimal(value)) else None
        case None => None
      },
      timestamp = doc.getAs[Long]("timestamp").get
    ))
  }

  implicit object ReceiptEntityBSONWriter extends BSONDocumentWriter[ReceiptEntity] {

    def write(receiptEntity: ReceiptEntity): BSONDocument = {
      BSONDocument(
        "_id" -> receiptEntity.id,
        "userId" -> receiptEntity.userId,
        "fileId" -> receiptEntity.files,
        "description" -> receiptEntity.description,
        "total" -> receiptEntity.total.toString,
        "timestamp" -> receiptEntity.timestamp
      )
    }
  }

}
