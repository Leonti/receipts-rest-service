package interpreters

import java.io.{File, FileOutputStream, InputStream}

import model.{FileEntity, PendingFile, StoredFile}
import algebras.FileAlg
import queue.files.ReceiptFileQueue
import repository.{PendingFileRepository, StoredFileRepository}
import service.FileService
import java.nio.file.{Files, StandardCopyOption}
import cats.effect.IO
import com.twitter.io.Buf
import queue.Models.JobId

class FileInterpreterTagless(
    storedFileRepository: StoredFileRepository,
    pendingFileRepository: PendingFileRepository,
    receiptFileQueue: ReceiptFileQueue,
    fileService: FileService
) extends FileAlg[IO] {
  import scala.concurrent.ExecutionContext.Implicits.global // TODO don't use global execution context

  override def submitPendingFile(pendingFile: PendingFile): IO[PendingFile] = IO.fromFuture(IO(pendingFileRepository.save(pendingFile)))
  override def submitToFileQueue(userId: String, receiptId: String, file: File, fileExt: String, pendingFileId: String): IO[JobId] =
    receiptFileQueue.submitFile(userId, receiptId, file, fileExt, pendingFileId)
  override def moveFile(src: File, dst: File): IO[Unit] = IO {
    Files.move(src.toPath, dst.toPath)
    ()
  }
  override def saveFile(userId: String, file: File, ext: String): IO[Seq[FileEntity]] =
    IO.fromFuture(IO(fileService.save(userId, file, ext)))
  override def saveStoredFile(storedFile: StoredFile): IO[Unit] = IO.fromFuture(IO(storedFileRepository.save(storedFile).map(_ => ())))
  override def findByMd5(userId: String, md5: String): IO[Seq[StoredFile]] =
    IO.fromFuture(IO(storedFileRepository.findForUserIdAndMd5(userId, md5)))
  override def deleteStoredFile(storedFileId: String): IO[Unit]                      = IO.fromFuture(IO(storedFileRepository.deleteById(storedFileId)))
  override def fetchFileInputStream(userId: String, fileId: String): IO[InputStream] = IO(fileService.fetchInputStream(userId, fileId))
  override def streamToFile(source: InputStream, file: File): IO[File] = IO { // TODO: maybe use IO.bracket to close in a nice manner
    java.nio.file.Files.copy(source, file.toPath, StandardCopyOption.REPLACE_EXISTING)

    source.close()
    file
  }
  override def deleteFile(userId: String, fileId: String): IO[Unit] = IO.fromFuture(IO(fileService.delete(userId, fileId)))
  override def removeFile(file: File): IO[Unit]                     = IO { file.delete }.map(_ => ()) // TODO use pure
  override def calculateMd5(file: File): IO[String]                 = IO { fileService.md5(file) } // TODO use pure
  override def bufToFile(src: Buf, dst: File): IO[Unit] = IO {
    src match {
      case Buf.ByteArray.Owned(bytes, _, _) =>
        val fw = new FileOutputStream(dst)
        fw.write(bytes)
        fw.close()
    }
  }
}
