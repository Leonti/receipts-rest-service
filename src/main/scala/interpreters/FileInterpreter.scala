package interpreters

import java.io.File

import cats.~>
import model.PendingFile
import ops.FileOps._
import queue.files.ReceiptFileQueue
import repository.PendingFileRepository
import service.FileService
import java.nio.file.Files

import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import scala.concurrent.Future

class FileInterpreter(
    pendingFileRepository: PendingFileRepository,
    receiptFileQueue: ReceiptFileQueue,
    fileService: FileService
)(implicit materializer: ActorMaterializer)
    extends (FileOp ~> Future) {
  import scala.concurrent.ExecutionContext.Implicits.global

  def apply[A](i: FileOp[A]): Future[A] = i match {
    case SubmitPendingFile(pendingFile: PendingFile) =>
      pendingFileRepository.save(pendingFile)
    case SubmitToFileQueue(userId: String, receiptId: String, file: File, fileExt: String, pendingFileId: String) =>
      receiptFileQueue.submitFile(userId, receiptId, file, fileExt, pendingFileId)
    case MoveFile(src: File, dst: File) =>
      Future.successful {
        Files.move(src.toPath, dst.toPath)
        ()
      }
    case SaveFile(userId: String, file: File, ext: String) => fileService.save(userId, file, ext)
    case FetchFile(userId: String, fileId: String)         => Future.successful(fileService.fetch(userId, fileId))
    case DeleteFile(userId: String, fileId: String)        => fileService.delete(userId, fileId)
    case RemoveFile(file: File)                            => Future { file.delete }.map(_ => ())
    case SourceToFile(source: Source[ByteString, Future[IOResult]], file: File) =>
      source
        .to(FileIO.toPath(file.toPath))
        .run()
        .map(_ => file)
  }
}
