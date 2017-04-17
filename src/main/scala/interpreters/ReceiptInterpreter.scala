package interpreters

import cats.~>
import model.{FileEntity, ReceiptEntity}
import ops.ReceiptOps._
import repository.{OcrRepository, ReceiptRepository}

import scala.concurrent.Future

class ReceiptInterpreter(receiptRepository: ReceiptRepository, ocrRepository: OcrRepository) extends (ReceiptOp ~> Future) {

  def apply[A](i: ReceiptOp[A]): Future[A] = i match {
    case GetReceipt(id: String)                                => receiptRepository.findById(id)
    case DeleteReceipt(id: String)                             => receiptRepository.deleteById(id)
    case SaveReceipt(id: String, receipt: ReceiptEntity)       => receiptRepository.save(receipt)
    case GetReceipts(ids: Seq[String])                         => receiptRepository.findByIds(ids)
    case UserReceipts(userId: String)                          => receiptRepository.findForUserId(userId)
    case FindOcrByText(userId: String, query: String)          => ocrRepository.findTextOnlyForUserId(userId, query)
    case AddFileToReceipt(receiptId: String, file: FileEntity) => receiptRepository.addFileToReceipt(receiptId, file)
  }

}
