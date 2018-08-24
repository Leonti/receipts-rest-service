package interpreters

import java.io.{File, FileOutputStream, InputStream}

import model.{FileEntity, PendingFile, StoredFile}
import algebras.FileAlg
import queue.files.ReceiptFileQueue
import repository.{PendingFileRepository, StoredFileRepository}
import service.FileService
import java.nio.file.Files

import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import cats.effect.IO
import com.twitter.io.Buf
import queue.Models.JobId
import fs2.Stream

import scala.concurrent.Future

class FileInterpreterTagless(
    storedFileRepository: StoredFileRepository,
    pendingFileRepository: PendingFileRepository,
    receiptFileQueue: ReceiptFileQueue,
    fileService: FileService
)(implicit materializer: ActorMaterializer)
    extends FileAlg[IO] {
  import scala.concurrent.ExecutionContext.Implicits.global // TODO don't use global execution context

  override def submitPendingFile(pendingFile: PendingFile): IO[PendingFile] = IO.fromFuture(IO(pendingFileRepository.save(pendingFile)))
  override def submitToFileQueue(userId: String, receiptId: String, file: File, fileExt: String, pendingFileId: String): IO[JobId] =
    IO.fromFuture(IO(receiptFileQueue.submitFile(userId, receiptId, file, fileExt, pendingFileId)))
  override def moveFile(src: File, dst: File): IO[Unit] = IO {
    Files.move(src.toPath, dst.toPath)
    ()
  }
  override def saveFile(userId: String, file: File, ext: String): IO[Seq[FileEntity]] = IO.fromFuture(IO(fileService.save(userId, file, ext)))
  override def saveStoredFile(storedFile: StoredFile): IO[Unit]                       = IO.fromFuture(IO(storedFileRepository.save(storedFile).map(_ => ())))
  override def findByMd5(userId: String, md5: String): IO[Seq[StoredFile]]            = IO.fromFuture(IO(storedFileRepository.findForUserIdAndMd5(userId, md5)))
  override def deleteStoredFile(storedFileId: String): IO[Unit]                       = IO.fromFuture(IO(storedFileRepository.deleteById(storedFileId)))
  override def fetchFile(userId: String, fileId: String): IO[Source[ByteString, Future[IOResult]]] =
    IO(fileService.fetch(userId, fileId))
  override def fetchFileInputStream(
                             userId: String,
                             fileId: String): IO[InputStream] = IO(fileService.fetchInputStream(userId, fileId))
  override def sourceToFile(source: Source[ByteString, Future[IOResult]], file: File): IO[File] = IO.fromFuture(IO {
    source
      .to(FileIO.toPath(file.toPath))
      .run()
      .map(_ => file)
  })
  override def fs2StreamToFile(source: Stream[IO, Byte],
                               file: File): IO[File] = source.to(fs2.io.file.writeAll(file.toPath)).compile.drain.map(_ => file)
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
