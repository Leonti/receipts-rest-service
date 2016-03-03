package service

import model.ReceiptEntity
import repository.ReceiptRepository

import scala.concurrent.{ExecutionContext, Future}

class ReceiptService (receiptRepository: ReceiptRepository) {

  def findForUserId(userId: String): Future[List[ReceiptEntity]] = receiptRepository.findForUserId(userId)

  def createReceipt(userId: String, fileId: String): Future[ReceiptEntity] = {
      val receiptEntity = ReceiptEntity(userId = userId, files = List(fileId))
      receiptRepository.save(receiptEntity)
  }

  def findById(id: String)(implicit ec: ExecutionContext): Future[Option[ReceiptEntity]] =
    receiptRepository.findById(id)

  def save(receiptEntity: ReceiptEntity): Future[ReceiptEntity] = receiptRepository.save(receiptEntity)

  def addFileToReceipt(receiptId: String, fileId: String)(implicit ec: ExecutionContext) : Future[Option[ReceiptEntity]] = {

    receiptRepository.findById(receiptId).flatMap((receiptEntity: Option[ReceiptEntity]) => receiptEntity match {
      case Some(receipt: ReceiptEntity) => save(receipt.copy(files = receipt.files :+ fileId)).map(result => Some(result))
      case None => Future(None)
    })


  }
}
