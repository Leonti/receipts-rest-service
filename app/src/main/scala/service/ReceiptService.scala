package service

import java.io.File

import akka.http.scaladsl.server.directives.FileInfo
import algebras.{FileAlg, OcrAlg, RandomAlg, ReceiptAlg}
import cats.Monad
import model._
import io.circe.syntax._
import io.circe.parser._
import gnieh.diffson.circe._
import cats.implicits._
import cats.data.EitherT

import scala.language.higherKinds

object ReceiptErrors {
  sealed trait Error
  final case class FileAlreadyExists()                extends Error
  final case class ReceiptNotFound(receiptId: String) extends Error
}

object Patch {
  val applyPatch: (ReceiptEntity, JsonPatch) => ReceiptEntity = (receiptEntity, jsonPatch) => {
    val asJson: String =
      receiptEntity.copy(total = if (receiptEntity.total.isDefined) receiptEntity.total else Some(BigDecimal("0"))).asJson.noSpaces
    val patched: String = jsonPatch(asJson)
    decode[ReceiptEntity](patched).toSeq.head
  }
}

class ReceiptPrograms[F[_]: Monad](receiptAlg: ReceiptAlg[F], fileAlg: FileAlg[F], randomAlg: RandomAlg[F], ocrAlg: OcrAlg[F]) {
  import receiptAlg._, fileAlg._, randomAlg._, ocrAlg._
  import ReceiptErrors._

  private def receiptsForQuery(userId: UserId, query: String): F[Seq[ReceiptEntity]] = {
    for {
      receiptIds <- findIdsByText(userId.value, query)
      receipts   <- getReceipts(userId, receiptIds)
    } yield receipts
  }

  def findForUser(userId: UserId, lastModifiedOption: Option[Long] = None, queryOption: Option[String] = None): F[Seq[ReceiptEntity]] =
    for {

      unfilteredReceipts <- queryOption
        .flatMap(query => if (query.trim.isEmpty) None else Some(query))
        .map(query => receiptsForQuery(userId, query))
        .getOrElse(userReceipts(userId))

      receipts = unfilteredReceipts.filter(receiptEntity => receiptEntity.lastModified > lastModifiedOption.getOrElse(0l))
    } yield receipts

  private def ext(fileName: String): String = fileName.split("\\.")(1)
  private def submitPF(uploadsLocation: String, userId: UserId, receiptId: String, file: File, fileName: String): F[PendingFile] =
    for {
      pendingFileId <- generateGuid()
      pendingFile <- submitPendingFile(
        PendingFile(
          id = pendingFileId,
          userId = userId.value,
          receiptId = receiptId
        )
      )
      _ <- submitToFileQueue(userId.value, receiptId, file, ext(fileName), pendingFile.id)
    } yield pendingFile

  private def validateExistingFile(haveExisting: Boolean): EitherT[F, Error, Unit] =
    if (haveExisting)
      EitherT.left[Unit](Monad[F].pure(FileAlreadyExists()))
    else
      EitherT.right[Error](Monad[F].pure(()))

  def createReceipt(uploadsLocation: String, userId: UserId, receiptUpload: ReceiptUpload): F[Either[Error, ReceiptEntity]] = {
    val eitherT: EitherT[F, Error, ReceiptEntity] = for {
      md5                     <- EitherT.right[Error](calculateMd5(receiptUpload.receipt))
      exitingFilesWithSameMd5 <- EitherT.right[Error](findByMd5(userId.value, md5))
      _                       <- validateExistingFile(exitingFilesWithSameMd5.nonEmpty)
      receiptId               <- EitherT.right[Error](generateGuid())
      currentTimeMillis       <- EitherT.right[Error](getTime())
      receipt = ReceiptEntity(
        id = receiptId,
        userId = userId.value,
        total = receiptUpload.total,
        description = receiptUpload.description,
        timestamp = currentTimeMillis,
        lastModified = currentTimeMillis,
        transactionTime = receiptUpload.transactionTime,
        tags = receiptUpload.tags,
        files = List()
      )
      _ <- EitherT.right[Error](saveReceipt(userId, receiptId, receipt))
      _ <- EitherT.right[Error](submitPF(uploadsLocation, userId, receiptId, receiptUpload.receipt, receiptUpload.fileName))
    } yield receipt

    eitherT.value
  }

  type OptionalReceipt = Option[ReceiptEntity]

  def addUploadedFileToReceipt(uploadsLocation: String,
                               userId: UserId,
                               receiptId: String,
                               metadata: FileInfo,
                               file: File): F[Either[Error, PendingFile]] = {
    val eitherT: EitherT[F, Error, PendingFile] = for {
      md5                     <- EitherT.right[Error](calculateMd5(file))
      exitingFilesWithSameMd5 <- EitherT.right[Error](findByMd5(userId.value, md5))
      _                       <- validateExistingFile(exitingFilesWithSameMd5.nonEmpty)
      receiptOption           <- EitherT.right[Error](getReceipt(userId, receiptId))
      pendingFile <- receiptOption
        .fold(EitherT.left[PendingFile](Monad[F].pure(ReceiptNotFound(receiptId))): EitherT[F, Error, PendingFile])(_ =>
          EitherT.right[Error](submitPF(uploadsLocation, userId, receiptId, file, metadata.fileName)))
    } yield pendingFile

    eitherT.value
  }

  def patchReceipt(userId: UserId, receiptId: String, jsonPatch: JsonPatch): F[Option[ReceiptEntity]] =
    for {
      receiptOption <- getReceipt(userId, receiptId)
      patchedReceipt = receiptOption.map(r => Patch.applyPatch(r, jsonPatch))
      currentTime <- getTime()
      _ <- if (patchedReceipt.isDefined) {
        saveReceipt(userId, receiptId, patchedReceipt.get.copy(lastModified = currentTime))
      } else {
        Monad[F].pure(())
      }
    } yield patchedReceipt

  def findById(userId: UserId, receiptId: String): F[Option[ReceiptEntity]] = getReceipt(userId, receiptId)

  private def removeReceiptFiles(userId: UserId, files: List[FileEntity]): F[List[Unit]] =
    files
      .map(file =>
        for {
          _ <- deleteFile(userId.value, file.id)
          r <- deleteStoredFile(file.id)
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

  def receiptFileWithExtension(userId: UserId, receiptId: String, fileId: String): F[Option[FileToServe]] =
    for {
      receiptOption <- getReceipt(userId, receiptId)
      fileToServeOption <- if (receiptOption.isDefined) {
        val extOption = receiptOption.get.files.find(_.id == fileId).map(_.ext)
        fetchFile(receiptOption.get.userId, fileId).map(source => extOption.map(ext => FileToServe(source, ext)))
      } else {
        Monad[F].pure(None)
      }
    } yield fileToServeOption
}
