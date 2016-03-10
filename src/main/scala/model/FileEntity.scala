package model

import reactivemongo.bson.{BSONDocumentWriter, BSONDocument, BSONDocumentReader}

case class FileEntity(id: String = java.util.UUID.randomUUID.toString, ext: String, timestamp: Long = System.currentTimeMillis)

object FileEntity {
  implicit object FileEntityBSONReader extends BSONDocumentReader[FileEntity] {

    def read(doc: BSONDocument): FileEntity = Serialization.deserialize(doc, FileEntity(
      id = doc.getAs[String]("_id").get,
      ext = doc.getAs[String]("ext").get,
      timestamp = doc.getAs[Long]("timestamp").get
    ))
  }

  implicit object FileEntityBSONWriter extends BSONDocumentWriter[FileEntity] {

    def write(fileEntity: FileEntity): BSONDocument = {
      BSONDocument(
        "_id" -> fileEntity.id,
        "ext" -> fileEntity.ext,
        "timestamp" -> fileEntity.timestamp
      )
    }
  }
}
