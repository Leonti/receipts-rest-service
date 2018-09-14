package model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

case class PendingFile(id: String, userId: String, receiptId: String) extends WithId

object PendingFile {

  implicit val pendingFileDecoder: Decoder[PendingFile] = deriveDecoder
  implicit val pendingFileEncoder: Encoder[PendingFile] = deriveEncoder

  implicit object PendingFileBSONReader extends BSONDocumentReader[PendingFile] {

    def read(doc: BSONDocument): PendingFile =
      Serialization.deserialize(doc,
                                PendingFile(
                                  id = doc.getAs[String]("_id").get,
                                  userId = doc.getAs[String]("userId").get,
                                  receiptId = doc.getAs[String]("receiptId").get
                                ))
  }

  implicit object PendingFileBSONWriter extends BSONDocumentWriter[PendingFile] {

    def write(pendingFile: PendingFile): BSONDocument = {
      BSONDocument(
        "_id"       -> pendingFile.id,
        "userId"    -> pendingFile.userId,
        "receiptId" -> pendingFile.receiptId
      )
    }
  }
}
