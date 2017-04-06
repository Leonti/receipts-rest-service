package service

import java.util.concurrent.Executors

import model.{FileEntity, OcrEntity, OcrTextOnly, ReceiptEntity}
import ocr.model.OcrTextAnnotation
import repository.{OcrRepository, ReceiptRepository}

import scala.concurrent.{ExecutionContext, Future}

class ReceiptService(receiptRepository: ReceiptRepository, ocrRepository: OcrRepository) {

  implicit val executor: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def findForUserId(userId: String, lastModifiedOption: Option[Long] = None): Future[List[ReceiptEntity]] = {
    receiptRepository.findForUserId(userId)
      .map(_.filter(receiptEntity => receiptEntity.lastModified > lastModifiedOption.getOrElse(0l)))
  }

  def createReceipt(userId: String, total: Option[BigDecimal], description: String, transactionTime: Long, tags: List[String]):
    Future[ReceiptEntity] = {
      val receiptEntity = ReceiptEntity(
        userId = userId,
        total = total,
        description = description,
        transactionTime = transactionTime,
        tags = tags,
        files = List())
      receiptRepository.save(receiptEntity)
  }

  def findById(id: String): Future[Option[ReceiptEntity]] =
    receiptRepository.findById(id)

  def save(receiptEntity: ReceiptEntity): Future[ReceiptEntity] = receiptRepository
    .save(receiptEntity.copy(lastModified = System.currentTimeMillis()))

  def addFileToReceipt(receiptId: String, file: FileEntity) : Future[Option[ReceiptEntity]] =
    receiptRepository.addFileToReceipt(receiptId, file).flatMap(_ => receiptRepository.findById(receiptId))

  def saveOcrResult(userId: String, receiptId: String, ocrResult: OcrTextAnnotation): Future[OcrEntity] =
    ocrRepository.save(OcrEntity(userId = userId, id = receiptId, result = ocrResult))

  def findByText(userId: String, query: String): Future[List[ReceiptEntity]] =
    for {
      ocrTexts: List[OcrTextOnly] <- ocrRepository.findTextOnlyForUserId(userId = userId, query = query)
      receiptIds: List[String] = ocrTexts.map(_.id)
      results <- receiptRepository.findByIds(receiptIds)
    } yield results

  def delete(receiptId: String): Future[Unit] = for {
    _ <- receiptRepository.deleteById(receiptId)
    _ <- ocrRepository.deleteById(receiptId)
    } yield Unit
}
