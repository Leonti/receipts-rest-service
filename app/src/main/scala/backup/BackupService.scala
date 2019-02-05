package backup

import algebras.{ReceiptStoreAlg, RemoteFileAlg}
import model.{FileEntity, ReceiptEntity, RemoteFileId}
import java.nio.charset.StandardCharsets

import cats.Monad
import cats.effect.{ConcurrentEffect, ContextShift}
import model.UserId
import io.circe.syntax._
import cats.implicits._
import fs2.Stream

import scala.concurrent.ExecutionContext

case class ReceiptsBackup[F[_]](source: Stream[F, Byte], filename: String)

class BackupService[F[_]: Monad](receiptAlg: ReceiptStoreAlg[F], remoteFileAlg: RemoteFileAlg[F]) {

  case class FileToZip(path: String, source: Stream[F, Byte])

  private def fetchFilesToZip(userId: UserId): F[List[FileToZip]] = {
    val receiptWithMainFiles: ReceiptEntity => ReceiptEntity =
      receipt => receipt.copy(files = receipt.files.filter(_.parentId.isEmpty))

    def fileToZip(fileEntity: FileEntity): F[FileToZip] =
      for {
        source <- remoteFileAlg.remoteFileStream(RemoteFileId(userId, fileEntity.id))
      } yield
        FileToZip(
          path = fileEntity.id + "." + fileEntity.ext,
          source = source
        )

    val receiptJsonEntry: Seq[ReceiptEntity] => FileToZip = receipts => {
      val receiptsAsJson = receipts.asJson.spaces2
      FileToZip(
        path = "receipts.json",
        source = Stream.emits(receiptsAsJson.getBytes(StandardCharsets.UTF_8))
      )
    }

    for {
      receipts <- receiptAlg.userReceipts(userId).map(_.map(receiptWithMainFiles))
      files    <- receipts.flatMap(_.files).toList.traverse(fileToZip)
    } yield files.++(List(receiptJsonEntry(receipts)))
  }

  def createUserBackup(userId: UserId)(implicit ce: ConcurrentEffect[F], cs: ContextShift[F], ec: ExecutionContext): F[ReceiptsBackup[F]] =
    for {
      filesToZip <- fetchFilesToZip(userId)
    } yield ReceiptsBackup[F](source = Fs2Zip.zip(Stream.emits(filesToZip.map(f => (f.path, f.source)))), filename = "backup.zip")
}
