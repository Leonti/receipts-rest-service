package service

import java.util.concurrent.Executors

import model.{FileEntity, ReceiptEntity}
import repository.ReceiptRepository

import scala.concurrent.{ExecutionContext, Future}

class ReceiptService(receiptRepository: ReceiptRepository) {

  implicit val executor: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def findForUserId(userId: String): Future[List[ReceiptEntity]] = receiptRepository.findForUserId(userId)

  def createReceipt(userId: String, total: Option[BigDecimal], description: String):
    Future[ReceiptEntity] = {
      val receiptEntity = ReceiptEntity(
        userId = userId,
        total = total,
        description = description,
        files = List())
      receiptRepository.save(receiptEntity)
  }

  def findById(id: String): Future[Option[ReceiptEntity]] =
    receiptRepository.findById(id)

  def save(receiptEntity: ReceiptEntity): Future[ReceiptEntity] = receiptRepository.save(receiptEntity)

  def addFileToReceipt(receiptId: String, file: FileEntity) : Future[Option[ReceiptEntity]] = {

    receiptRepository.findById(receiptId).flatMap((receiptEntity: Option[ReceiptEntity]) => receiptEntity match {
      case Some(receipt: ReceiptEntity) => save(receipt.copy(files = receipt.files :+ file)).map(result => Some(result))
      case None => Future(None)
    })
  }

  def delete(receiptId: String): Future[Unit] = receiptRepository.deleteById(receiptId).map(r => ())
}
