package service

import java.io._
import java.nio.charset.StandardCharsets
import java.util.zip.{ZipEntry, ZipOutputStream}

import algebras.{FileAlg, ReceiptAlg}
import cats.Monad
import cats.effect.IO
import model.{FileEntity, ReceiptEntity, UserId}
import io.circe.syntax._
import cats.implicits._
import fs2.Stream

import scala.concurrent.Future
import scala.language.postfixOps

import scala.concurrent.ExecutionContext.Implicits.global // FIXME

case class ReceiptsBackupIO(runSource: IO[Unit], source: InputStream, filename: String)

class BackupServiceIO[F[_]: Monad](receiptAlg: ReceiptAlg[F], fileAlg: FileAlg[F]) {

  case class FileToZip(path: String, source: InputStream)

  private def fetchFilesToZip(userId: UserId): F[List[FileToZip]] = {
    val receiptWithMainFiles: ReceiptEntity => ReceiptEntity =
      receipt => receipt.copy(files = receipt.files.filter(_.parentId.isEmpty))

    def fileToZip(fileEntity: FileEntity): F[FileToZip] =
      for {
        source <- fileAlg.fetchFileInputStream(userId.value, fileEntity.id)
      } yield FileToZip(
          path = fileEntity.id + "." + fileEntity.ext,
          source = source
        )

    val receiptJsonEntry: Seq[ReceiptEntity] => FileToZip = receipts => {
      val receiptsAsJson = receipts.asJson.spaces2
      FileToZip(
        path = "receipts.json",
        source = new ByteArrayInputStream(receiptsAsJson.getBytes(StandardCharsets.UTF_8))
      )
    }

    for {
      receipts <- receiptAlg.userReceipts(userId).map(_.map(receiptWithMainFiles))
      files <- receipts.flatMap(_.files).toList.traverse(fileToZip)
    } yield files.++(List(receiptJsonEntry(receipts)))
  }

  private def filesToStream(filesToZip: List[FileToZip])(zipOutputStream: ZipOutputStream): Stream[IO, Unit] =
    fs2.Stream.emits(filesToZip).evalMap[IO, Unit](fileToZip => IO {
      val zipEntry = new ZipEntry(fileToZip.path)
      zipOutputStream.putNextEntry(zipEntry)

      val bytes = new Array[Byte](1024) //1024 bytes - Buffer size
      Iterator
        .continually(fileToZip.source.read(bytes))
        .takeWhile(-1 !=)
        .foreach(read => zipOutputStream.write(bytes,0,read))

      zipOutputStream.closeEntry()
  })

  def createUserBackup(userId: UserId): F[ReceiptsBackupIO] =
    fetchFilesToZip(userId).map(filesToZip => {
      val inputStream  = new PipedInputStream()
      val outputStream = IO.fromFuture(IO(Future { new PipedOutputStream(inputStream) }))
      val runStream = Stream.bracket(outputStream.map(os => new ZipOutputStream(os)))(filesToStream(filesToZip), zipOutputStream => IO { zipOutputStream.close() }).compile.drain

      ReceiptsBackupIO(runSource = runStream, source = inputStream, filename = "backup.zip")
    })
}
