package interpreters

import java.io.File

import cats.~>
import model.PendingFile
import ops.FileOps._
import queue.files.ReceiptFileQueue
import repository.PendingFileRepository

import scala.concurrent.Future

class FileInterpreter(pendingFileRepository: PendingFileRepository, receiptFileQueue: ReceiptFileQueue) extends (FileOp ~> Future) {

  def apply[A](i: FileOp[A]): Future[A] = i match {
    case SubmitPendingFile(pendingFile: PendingFile) =>
      pendingFileRepository.save(pendingFile)
    case SubmitToFileQueue(userId: String, receiptId: String, file: File, fileExt: String, pendingFileId: String) =>
      receiptFileQueue.submitFile(userId, receiptId, file, fileExt, pendingFileId)
    //  case SaveFile(userId: String, file: File, ext: String) =>
    //  case FetchFile(userId: String, fileId: String) =>
    //  case DeleteFile(userId: String, fileId: String) =>
  }
}
