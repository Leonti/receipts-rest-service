package interpreters

import model.{FileEntity, ReceiptEntity}
import algebras.ReceiptAlg
import repository.{OcrRepository, ReceiptRepository}

import scala.concurrent.Future

class ReceiptInterpreterTagless(receiptRepository: ReceiptRepository, ocrRepository: OcrRepository) extends ReceiptAlg[Future] {
  override def getReceipt(id: String): Future[Option[ReceiptEntity]]                  = receiptRepository.findById(id)
  override def deleteReceipt(id: String): Future[Unit]                                = receiptRepository.deleteById(id)
  override def saveReceipt(id: String, receipt: ReceiptEntity): Future[ReceiptEntity] = receiptRepository.save(receipt)
  override def getReceipts(ids: Seq[String]): Future[Seq[ReceiptEntity]]              = receiptRepository.findByIds(ids)
  override def userReceipts(userId: String): Future[Seq[ReceiptEntity]]               = receiptRepository.findForUserId(userId)
  override def addFileToReceipt(receiptId: String, file: FileEntity): Future[Unit]    = receiptRepository.addFileToReceipt(receiptId, file)
}
