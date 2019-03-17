package ocr

import model.WithId

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
