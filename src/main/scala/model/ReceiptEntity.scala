package model

import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

case class ReceiptEntity(
                          id: String = java.util.UUID.randomUUID.toString,
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

  implicit object ReceiptEntityBSONReader extends BSONDocumentReader[ReceiptEntity] {

    def read(doc: BSONDocument): ReceiptEntity = Serialization.deserialize(doc, ReceiptEntity(
      id = doc.getAs[String]("_id").get,
      userId = doc.getAs[String]("userId").get,
      files = doc.getAs[List[FileEntity]]("files").get,
      description = doc.getAs[String]("description").get,
      total = doc.getAs[String]("total") match {
        case Some(value) => if (value != "None") Some(BigDecimal(value.replace("Some(", "").replace(")", ""))) else None
        case None => None
      },
      timestamp = doc.getAs[Long]("timestamp").get,
      lastModified = doc.getAs[Long]("lastModified").get,
      transactionTime = doc.getAs[Long]("transactionTime").get,
      tags = doc.getAs[List[String]]("tags").get
    ))
  }

  implicit object ReceiptEntityBSONWriter extends BSONDocumentWriter[ReceiptEntity] {

    def write(receiptEntity: ReceiptEntity): BSONDocument = {
      BSONDocument(
        "_id" -> receiptEntity.id,
        "userId" -> receiptEntity.userId,
        "files" -> receiptEntity.files,
        "description" -> receiptEntity.description,
        "total" -> receiptEntity.total.toString,
        "timestamp" -> receiptEntity.timestamp,
        "lastModified" -> receiptEntity.lastModified,
        "transactionTime" -> receiptEntity.transactionTime,
        "tags" -> receiptEntity.tags
      )
    }
  }

}
