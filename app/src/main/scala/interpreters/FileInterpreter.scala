package interpreters

import java.io.{File, FileOutputStream}

import model.{FileEntity, PendingFile, StoredFile}
import algebras.FileAlg
import queue.files.ReceiptFileQueue
import repository.{PendingFileRepository, StoredFileRepository}
import service.FileService
import java.nio.file.Files

import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.twitter.io.Buf
import queue.Models.JobId

import scala.concurrent.Future

class FileInterpreterTagless(
    storedFileRepository: StoredFileRepository,
    pendingFileRepository: PendingFileRepository,
    receiptFileQueue: ReceiptFileQueue,
    fileService: FileService
)(implicit materializer: ActorMaterializer)
    extends FileAlg[Future] {
  import scala.concurrent.ExecutionContext.Implicits.global // TODO don't use global execution context

  override def submitPendingFile(pendingFile: PendingFile): Future[PendingFile] = pendingFileRepository.save(pendingFile)
  override def submitToFileQueue(userId: String, receiptId: String, file: File, fileExt: String, pendingFileId: String): Future[JobId] =
    receiptFileQueue.submitFile(userId, receiptId, file, fileExt, pendingFileId)
  override def moveFile(src: File, dst: File): Future[Unit] = Future.successful { // TODO - use .pure
    Files.move(src.toPath, dst.toPath)
    ()
  }
  override def saveFile(userId: String, file: File, ext: String): Future[Seq[FileEntity]] = fileService.save(userId, file, ext)
  override def saveStoredFile(storedFile: StoredFile): Future[Unit]                       = storedFileRepository.save(storedFile).map(_ => ())
  override def findByMd5(userId: String, md5: String): Future[Seq[StoredFile]]            = storedFileRepository.findForUserIdAndMd5(userId, md5)
  override def deleteStoredFile(storedFileId: String): Future[Unit]                       = storedFileRepository.deleteById(storedFileId)
  override def fetchFile(userId: String, fileId: String): Future[Source[ByteString, Future[IOResult]]] =
    Future.successful(fileService.fetch(userId, fileId))
  override def sourceToFile(source: Source[ByteString, Future[IOResult]], file: File): Future[File] =
    source
      .to(FileIO.toPath(file.toPath))
      .run()
      .map(_ => file)
  override def deleteFile(userId: String, fileId: String): Future[Unit] = fileService.delete(userId, fileId)
  override def removeFile(file: File): Future[Unit]                     = Future { file.delete }.map(_ => ()) // TODO use pure
  override def calculateMd5(file: File): Future[String]                 = Future { fileService.md5(file) } // TODO use pure
  override def bufToFile(src: Buf, dst: File): Future[Unit] = Future {
    src match {
      case Buf.ByteArray.Owned(bytes, _, _) =>
        val fw = new FileOutputStream(dst)
        fw.write(bytes)
        fw.close()
    }
  }
}
