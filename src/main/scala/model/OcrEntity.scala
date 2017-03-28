package model

import ocr.model.OcrTextAnnotation
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

case class OcrEntity(
                    id: String,
                    userId: String,
                    result: OcrTextAnnotation
                    ) extends WithId

object OcrEntity {

  implicit object OcrEntityBSONReader extends BSONDocumentReader[OcrEntity] {

    def read(doc: BSONDocument): OcrEntity = Serialization.deserialize(doc, OcrEntity(
      id = doc.getAs[String]("_id").get,
      userId = doc.getAs[String]("userId").get,
      result = doc.getAs[OcrTextAnnotation]("result").get
    )
    )
  }

  implicit object OcrEntityBSONWriter extends BSONDocumentWriter[OcrEntity] {

    def write(ocrResult: OcrEntity): BSONDocument = {
      BSONDocument(
        "_id" -> ocrResult.id,
        "userId" -> ocrResult.userId,
        "result" -> ocrResult.result
      )
    }
  }
}