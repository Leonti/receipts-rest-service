package interpreters

import algebras.ReceiptStoreAlg
import cats.effect.IO
import receipt.{FileEntity, ReceiptEntity}
import repository.ReceiptRepository
import user.UserId

class ReceiptStoreMongo(receiptRepository: ReceiptRepository) extends ReceiptStoreAlg[IO] {
  override def getReceipt(userId: UserId, id: String): IO[Option[ReceiptEntity]] = IO.fromFuture(IO(receiptRepository.findById(id)))
  override def deleteReceipt(userId: UserId, id: String): IO[Unit]               = IO.fromFuture(IO(receiptRepository.deleteById(id)))
  override def saveReceipt(receipt: ReceiptEntity): IO[ReceiptEntity] =
    IO.fromFuture(IO(receiptRepository.save(receipt)))
  override def getReceipts(userId: UserId, ids: Seq[String]): IO[Seq[ReceiptEntity]] = IO.fromFuture(IO(receiptRepository.findByIds(ids)))
  override def userReceipts(userId: UserId): IO[Seq[ReceiptEntity]]                  = IO.fromFuture(IO(receiptRepository.findForUserId(userId.value)))
  override def addFileToReceipt(userId: UserId, receiptId: String, file: FileEntity): IO[Unit] =
    IO.fromFuture(IO(receiptRepository.addFileToReceipt(receiptId, file)))
}
