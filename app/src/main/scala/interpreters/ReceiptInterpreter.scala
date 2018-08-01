package interpreters

import cats.~>
import model.{FileEntity, ReceiptEntity}
import ops.ReceiptOps._
import algebras.ReceiptAlg
import repository.{OcrRepository, ReceiptRepository}

import scala.concurrent.Future

class ReceiptInterpreter(receiptRepository: ReceiptRepository, ocrRepository: OcrRepository) extends (ReceiptOp ~> Future) {

  def apply[A](i: ReceiptOp[A]): Future[A] = i match {
    case GetReceipt(id: String)                                => receiptRepository.findById(id)
    case DeleteReceipt(id: String)                             => receiptRepository.deleteById(id)
    case SaveReceipt(id: String, receipt: ReceiptEntity)       => receiptRepository.save(receipt)
    case GetReceipts(ids: Seq[String])                         => receiptRepository.findByIds(ids)
    case UserReceipts(userId: String)                          => receiptRepository.findForUserId(userId)
    case AddFileToReceipt(receiptId: String, file: FileEntity) => receiptRepository.addFileToReceipt(receiptId, file)
  }

}

class ReceiptInterpreterTagless(receiptRepository: ReceiptRepository, ocrRepository: OcrRepository) extends ReceiptAlg[Future] {
  override def getReceipt(
      id: String): Future[Option[ReceiptEntity]] = receiptRepository.findById(id)
  override def deleteReceipt(id: String): Future[Unit] = receiptRepository.deleteById(id)
  override def saveReceipt(id: String,
                           receipt: ReceiptEntity): Future[ReceiptEntity] = receiptRepository.save(receipt)
  override def getReceipts(
      ids: Seq[String]): Future[Seq[ReceiptEntity]] = receiptRepository.findByIds(ids)
  override def userReceipts(userId: String): Future[Seq[ReceiptEntity]] = receiptRepository.findForUserId(userId)
override def addFileToReceipt(receiptId: String,
                              file: FileEntity): Future[Unit] = receiptRepository.addFileToReceipt(receiptId, file)
}
