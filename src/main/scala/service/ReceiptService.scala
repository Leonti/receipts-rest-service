package service

import java.util.concurrent.Executors

import model.{FileEntity, ReceiptEntity}
import repository.ReceiptRepository

import scala.concurrent.{ExecutionContext, Future}

class ReceiptService(receiptRepository: ReceiptRepository) {

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

  def delete(receiptId: String): Future[Unit] = receiptRepository.deleteById(receiptId).map(r => ())
}
