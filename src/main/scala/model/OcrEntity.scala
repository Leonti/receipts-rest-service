package model

import ocr.model.OcrTextAnnotation
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, Macros}

case class OcrEntity(
                    id: String,
                    userId: String,
                    result: OcrTextAnnotation
                    ) extends WithId

case class OcrText(text: String)

case class OcrTextOnly(
                        id: String,
                        result: OcrText
                      ) extends WithId

object OcrTextOnly {
  implicit def ocrTextReader: BSONDocumentReader[OcrText] = Macros.reader[OcrText]
  implicit object OcrTextOnlyBSONReader extends BSONDocumentReader[OcrTextOnly] {

    def read(doc: BSONDocument): OcrTextOnly = Serialization.deserialize(doc, OcrTextOnly(
      id = doc.getAs[String]("_id").get,
      result = doc.getAs[OcrText]("result").get
    ))
  }
}

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