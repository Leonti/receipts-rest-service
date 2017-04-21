package interpreters

import java.io.File

import cats.~>
import model.PendingFile
import ops.FileOps._
import queue.files.ReceiptFileQueue
import repository.PendingFileRepository
import service.FileService

import scala.concurrent.Future

class FileInterpreter(pendingFileRepository: PendingFileRepository, receiptFileQueue: ReceiptFileQueue, fileService: FileService)
    extends (FileOp ~> Future) {
  import scala.concurrent.ExecutionContext.Implicits.global

  def apply[A](i: FileOp[A]): Future[A] = i match {
    case SubmitPendingFile(pendingFile: PendingFile) =>
      pendingFileRepository.save(pendingFile)
    case SubmitToFileQueue(userId: String, receiptId: String, file: File, fileExt: String, pendingFileId: String) =>
      receiptFileQueue.submitFile(userId, receiptId, file, fileExt, pendingFileId)
    case SaveFile(userId: String, file: File, ext: String) => fileService.save(userId, file, ext)
    case FetchFile(userId: String, fileId: String)         => Future.successful(fileService.fetch(userId, fileId))
    case DeleteFile(userId: String, fileId: String)        => fileService.delete(userId, fileId)
    case RemoveFile(file: File)                            => Future { file.delete }.map(_ => ())
  }
}
