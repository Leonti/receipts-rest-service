package model

import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

case class PendingFile(id: String, userId: String, receiptId: String) extends WithId

object PendingFile {

  implicit object PendingFileBSONReader extends BSONDocumentReader[PendingFile] {

    def read(doc: BSONDocument): PendingFile = Serialization.deserialize(doc, PendingFile(
      id = doc.getAs[String]("_id").get,
      userId = doc.getAs[String]("userId").get,
      receiptId = doc.getAs[String]("receiptId").get
      )
    )
  }

  implicit object PendingFileBSONWriter extends BSONDocumentWriter[PendingFile] {

    def write(pendingFile: PendingFile): BSONDocument = {
      BSONDocument(
        "_id" -> pendingFile.id,
        "userId" -> pendingFile.userId,
        "receiptId" -> pendingFile.receiptId
      )
    }
  }
}