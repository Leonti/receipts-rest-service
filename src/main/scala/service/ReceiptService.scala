package service

import model.{FileEntity, ReceiptEntity}
import repository.ReceiptRepository

import scala.concurrent.{ExecutionContext, Future}

class ReceiptService (receiptRepository: ReceiptRepository) {

  def findForUserId(userId: String): Future[List[ReceiptEntity]] = receiptRepository.findForUserId(userId)

  def createReceipt(userId: String, file: FileEntity, total: Option[BigDecimal], description: String):
    Future[ReceiptEntity] = {
      val receiptEntity = ReceiptEntity(
        userId = userId,
        total = total,
        description = description,
        files = List(file))
      receiptRepository.save(receiptEntity)
  }

  def findById(id: String)(implicit ec: ExecutionContext): Future[Option[ReceiptEntity]] =
    receiptRepository.findById(id)

  def save(receiptEntity: ReceiptEntity): Future[ReceiptEntity] = receiptRepository.save(receiptEntity)

  def addFileToReceipt(receiptId: String, file: FileEntity)(implicit ec: ExecutionContext) : Future[Option[ReceiptEntity]] = {

    receiptRepository.findById(receiptId).flatMap((receiptEntity: Option[ReceiptEntity]) => receiptEntity match {
      case Some(receipt: ReceiptEntity) => save(receipt.copy(files = receipt.files :+ file)).map(result => Some(result))
      case None => Future(None)
    })

  }
}
