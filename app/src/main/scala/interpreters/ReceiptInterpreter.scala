package interpreters

import model.{FileEntity, ReceiptEntity, UserId}
import algebras.ReceiptAlg
import repository.{OcrRepository, ReceiptRepository}

import scala.concurrent.Future

class ReceiptInterpreterTagless(receiptRepository: ReceiptRepository, ocrRepository: OcrRepository) extends ReceiptAlg[Future] {
  override def getReceipt(userId: UserId, id: String): Future[Option[ReceiptEntity]]                  = receiptRepository.findById(id)
  override def deleteReceipt(userId: UserId, id: String): Future[Unit]                                = receiptRepository.deleteById(id)
  override def saveReceipt(userId: UserId, id: String, receipt: ReceiptEntity): Future[ReceiptEntity] = receiptRepository.save(receipt)
  override def getReceipts(userId: UserId, ids: Seq[String]): Future[Seq[ReceiptEntity]]              = receiptRepository.findByIds(ids)
  override def userReceipts(userId: UserId): Future[Seq[ReceiptEntity]]                               = receiptRepository.findForUserId(userId.value)
  override def addFileToReceipt(userId: UserId, receiptId: String, file: FileEntity): Future[Unit] =
    receiptRepository.addFileToReceipt(receiptId, file)
}
