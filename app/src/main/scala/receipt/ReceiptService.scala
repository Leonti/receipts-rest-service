package receipt

import java.io.File

import algebras._
import cats.Monad
import io.circe.syntax._
import diffson.jsonpatch._
import cats.implicits._
import cats.data.EitherT
import fs2.Stream
import io.circe.Json
import pending.PendingFile
import queue.ReceiptFileJob
import user.UserId

import scala.language.higherKinds
import scala.util.Try

object ReceiptErrors {
  sealed trait Error
  final case class FileAlreadyExists()                extends Error
  final case class ReceiptNotFound(receiptId: String) extends Error
}

case class ReceiptField[F[_]](name: String, stream: Stream[F, Byte])
case class ReceiptForm[F[_]](
    receipt: ReceiptField[F],
    total: Option[BigDecimal],
    description: String,
    transactionTime: Long,
    tags: List[String]
)

object Patch {
  val applyPatch: (ReceiptEntity, JsonPatch[Json]) => ReceiptEntity = (receiptEntity, jsonPatch) => {
    val asJson: Json =
      receiptEntity.copy(total = if (receiptEntity.total.isDefined) receiptEntity.total else Some(BigDecimal("0"))).asJson
    val patched = jsonPatch[Try](asJson).toOption.get
    patched.as[ReceiptEntity].toOption.get
  }
}

class ReceiptPrograms[F[_]: Monad](uploadsLocation: String,
                                   receiptAlg: ReceiptStoreAlg[F],
                                   localFileAlg: LocalFileAlg[F],
                                   remoteFileAlg: RemoteFileAlg[F],
                                   fileStoreAlg: FileStoreAlg[F],
                                   pendingFileAlg: PendingFileAlg[F],
                                   queueAlg: QueueAlg[F],
                                   randomAlg: RandomAlg[F],
                                   ocrAlg: OcrAlg[F]) {
  import receiptAlg._, randomAlg._, ocrAlg._
  import ReceiptErrors._

  private def receiptsForQuery(userId: UserId, query: String): F[List[ReceiptEntity]] = {
    for {
      receiptIds <- findIdsByText(userId.value, query)
      receipts   <- getReceipts(userId, receiptIds)
    } yield receipts
  }

  def findForUser(userId: UserId, lastModifiedOption: Option[Long] = None, queryOption: Option[String] = None): F[List[ReceiptEntity]] =
    queryOption.flatMap(query => if (query.trim.isEmpty) None else Some(query)) match {
      case Some(query) => receiptsForQuery(userId, query).map(_.filter(_.lastModified > lastModifiedOption.getOrElse(0l)))
      case None =>
        lastModifiedOption match {
          case Some(lastModified) => recentUserReceipts(userId, lastModified)
          case None               => userReceipts(userId)
        }
    }

  private def ext(fileName: String): String = fileName.split("\\.")(1)
  private def submitPF(userId: UserId, receiptId: String, remoteFileId: RemoteFileId, ext: String): F[PendingFile] =
    for {
      pendingFileId <- generateGuid()
      pendingFile <- pendingFileAlg.savePendingFile(
        PendingFile(
          id = pendingFileId,
          userId = userId.value,
          receiptId = receiptId
        )
      )
      _ <- queueAlg.submit(
        ReceiptFileJob(
          userId = userId.value,
          receiptId = receiptId,
          remoteFileId = remoteFileId,
          fileExt = ext,
          pendingFileId = pendingFileId
        ))
    } yield pendingFile

  private def validateExistingFile(haveExisting: Boolean): EitherT[F, Error, Unit] =
    if (haveExisting)
      EitherT.left[Unit](Monad[F].pure(FileAlreadyExists()))
    else
      EitherT.right[Error](Monad[F].pure(()))

  def createReceipt(userId: UserId, receiptForm: ReceiptForm[F]): F[Either[Error, ReceiptEntity]] = {
    val eitherT: EitherT[F, Error, ReceiptEntity] = for {
      randomGuid <- EitherT.right[Error](generateGuid())
      tmpFile = new File(new File(uploadsLocation), randomGuid)
      _                       <- EitherT.right[Error](localFileAlg.streamToFile(receiptForm.receipt.stream, tmpFile))
      md5                     <- EitherT.right[Error](localFileAlg.getMd5(tmpFile))
      exitingFilesWithSameMd5 <- EitherT.right[Error](fileStoreAlg.findByMd5(userId, md5))
      _                       <- validateExistingFile(exitingFilesWithSameMd5.nonEmpty)
      receiptId               <- EitherT.right[Error](generateGuid())
      currentTimeMillis       <- EitherT.right[Error](getTime())
      remoteFileId = RemoteFileId(userId, receiptId)
      _ <- EitherT.right[Error](remoteFileAlg.saveRemoteFile(tmpFile, remoteFileId))
      _ <- EitherT.right[Error](
        fileStoreAlg.saveStoredFile(
          StoredFile(
            userId = userId.value,
            id = receiptId,
            md5 = md5,
          )))
      receipt = ReceiptEntity(
        id = receiptId,
        userId = userId.value,
        total = receiptForm.total,
        description = receiptForm.description,
        timestamp = currentTimeMillis,
        lastModified = currentTimeMillis,
        transactionTime = receiptForm.transactionTime,
        tags = receiptForm.tags
      )
      _ <- EitherT.right[Error](saveReceipt(receipt))
      _ <- EitherT.right[Error](submitPF(userId, receiptId, remoteFileId, ext(receiptForm.receipt.name)))
      _ <- EitherT.right[Error](localFileAlg.removeFile(tmpFile))
    } yield receipt

    eitherT.value
  }

  type OptionalReceipt = Option[ReceiptEntity]

  def patchReceipt(userId: UserId, receiptId: String, jsonPatch: JsonPatch[Json]): F[Option[ReceiptEntity]] =
    for {
      receiptOption <- getReceipt(userId, receiptId)
      patchedReceipt = receiptOption.map(r => Patch.applyPatch(r, jsonPatch))
      currentTime <- getTime()
      _ <- if (patchedReceipt.isDefined) {
        saveReceipt(patchedReceipt.get.copy(lastModified = currentTime))
      } else {
        Monad[F].pure(())
      }
    } yield patchedReceipt

  def findById(userId: UserId, receiptId: String): F[Option[ReceiptEntity]] = getReceipt(userId, receiptId)

  private def removeReceiptFiles(userId: UserId, files: List[FileEntity]): F[List[Unit]] =
    files
      .map(file =>
        for {
          _ <- remoteFileAlg.deleteRemoteFile(RemoteFileId(userId, file.id))
          r <- fileStoreAlg.deleteStoredFile(userId, file.id)
        } yield r)
      .sequence

  def removeReceipt(userId: UserId, receiptId: String): F[Option[Unit]] =
    for {
      receiptOption <- getReceipt(userId, receiptId)
      _             <- deleteReceipt(userId, receiptId)
      fileDeletionResult <- if (receiptOption.isDefined) {
        removeReceiptFiles(userId, receiptOption.get.files).map(_ => Some(()))
      } else {
        Monad[F].pure(None)
      }
    } yield fileDeletionResult

  def receiptFileWithExtension(userId: UserId, receiptId: String, fileId: String): F[Option[FileToServe[F]]] =
    for {
      receiptOption <- getReceipt(userId, receiptId)
      fileToServeOption <- if (receiptOption.isDefined) {
        val extOption = receiptOption.get.files.find(_.id == fileId).map(_.ext)
        remoteFileAlg
          .remoteFileStream(RemoteFileId(UserId(receiptOption.get.userId), fileId))
          .map(stream => extOption.map(ext => receipt.FileToServe[F](stream, ext)))
      } else {
        Monad[F].pure(None)
      }
    } yield fileToServeOption
}
