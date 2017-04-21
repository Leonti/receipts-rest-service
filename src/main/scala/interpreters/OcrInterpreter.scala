package interpreters

import java.io.File

import cats.~>
import model.OcrEntity
import ocr.model.OcrTextAnnotation
import ocr.service.OcrService
import ops.OcrOps.{OcrImage, OcrOp, SaveOcrResult}
import repository.OcrRepository

import scala.concurrent.Future

class OcrInterpreter(ocrRepository: OcrRepository, ocrService: OcrService) extends (OcrOp ~> Future) {

  def apply[A](i: OcrOp[A]): Future[A] = i match {
    case OcrImage(file: File) => ocrService.ocrImage(file)
    case SaveOcrResult(userId: String, receiptId: String, ocrResult: OcrTextAnnotation) =>
      ocrRepository.save(OcrEntity(userId = userId, id = receiptId, result = ocrResult))
  }

}
