package service

import java.io.File

import akka.http.scaladsl.server.directives.FileInfo
import algebras.{FileAlg, OcrAlg, RandomAlg, ReceiptAlg}
import cats.Monad
import model._
import routing.ParsedForm
import spray.json._
import gnieh.diffson.sprayJson._

import scala.util.Try
import cats.implicits._
import cats.data.EitherT

import scala.language.higherKinds

object ReceiptErrors {
  sealed trait Error
  final case class FileAlreadyExists()                extends Error
  final case class ReceiptNotFound(receiptId: String) extends Error
}

object Patch extends JsonProtocols {
  val applyPatch: (ReceiptEntity, String) => ReceiptEntity = (receiptEntity, jsonPatch) => {
    val asJson: String =
      receiptEntity.copy(total = if (receiptEntity.total.isDefined) receiptEntity.total else Some(BigDecimal("0"))).toJson.compactPrint
    val patched: String = JsonPatch.parse(jsonPatch).apply(asJson)
    patched.parseJson.convertTo[ReceiptEntity]
  }
}

class ReceiptPrograms[F[_]: Monad](receiptAlg: ReceiptAlg[F], fileAlg: FileAlg[F], randomAlg: RandomAlg[F], ocrAlg: OcrAlg[F]) {
  import receiptAlg._, fileAlg._, randomAlg._, ocrAlg._
  import ReceiptErrors._

  private def receiptsForQuery(userId: String, query: String): F[Seq[ReceiptEntity]] = {
    for {
      receiptIds <- findIdsByText(userId, query)
      receipts   <- getReceipts(receiptIds)
    } yield receipts
  }

  def findForUser(userId: String,
                  lastModifiedOption: Option[Long] = None,
                  queryOption: Option[String] = None): F[Seq[ReceiptEntity]] =
    for {

      unfilteredReceipts <- queryOption
        .flatMap(query => if (query.trim.isEmpty) None else Some(query))
        .map(query => receiptsForQuery(userId, query))
        .getOrElse(userReceipts(userId))

      receipts = unfilteredReceipts.filter(receiptEntity => receiptEntity.lastModified > lastModifiedOption.getOrElse(0l))
    } yield receipts

  private def ext(fileName: String): String = fileName.split("\\.")(1)
  private def submitPF(uploadsLocation: String, userId: String, receiptId: String, file: File, fileName: String): F[PendingFile] =
    for {
      pendingFileId <- generateGuid()
      pendingFile <- submitPendingFile(
        PendingFile(
          id = pendingFileId,
          userId = userId,
          receiptId = receiptId
        )
      )
      _               <- moveFile(file, new File(new File(uploadsLocation), pendingFileId))// TODO remove new File duplication
      _               <- submitToFileQueue(userId, receiptId, new File(new File(uploadsLocation), pendingFileId), ext(fileName), pendingFile.id)
    } yield pendingFile

  private def validateExistingFile(haveExisting: Boolean): EitherT[F, Error, Unit] =
    if (haveExisting)
      EitherT.left[Unit](Monad[F].pure(FileAlreadyExists()))
    else
      EitherT.right[Error](Monad[F].pure(()))

  def createReceipt(uploadsLocation: String, userId: String, parsedForm: ParsedForm): F[Either[Error, ReceiptEntity]] = {
    val eitherT: EitherT[F, Error, ReceiptEntity] = for {
      md5                     <- EitherT.right[Error](calculateMd5(parsedForm.files("receipt").file))
      exitingFilesWithSameMd5 <- EitherT.right[Error](findByMd5(userId, md5))
      _                       <- validateExistingFile(exitingFilesWithSameMd5.nonEmpty)
      receiptId               <- EitherT.right[Error](generateGuid())
      currentTimeMillis       <- EitherT.right[Error](getTime())
      uploadedFile = parsedForm.files("receipt")
      tags         = parsedForm.fields("tags")
      receipt = ReceiptEntity(
        id = receiptId,
        userId = userId,
        total = Try(BigDecimal(parsedForm.fields("total"))).map(Some(_)).getOrElse(None),
        description = parsedForm.fields("description"),
        timestamp = currentTimeMillis,
        lastModified = currentTimeMillis,
        transactionTime = parsedForm.fields("transactionTime").toLong,
        tags = if (tags.trim() == "") List() else tags.split(",").toList,
        files = List()
      )
      _ <- EitherT.right[Error](saveReceipt(receiptId, receipt))
      _ <- EitherT.right[Error](
        submitPF(uploadsLocation, userId, receiptId, uploadedFile.file, uploadedFile.fileInfo.fileName))
    } yield receipt

    eitherT.value
  }

  type OptionalReceipt = Option[ReceiptEntity]

  def addUploadedFileToReceipt(uploadsLocation: String,
                               userId: String,
                               receiptId: String,
                               metadata: FileInfo,
                               file: File): F[Either[Error, PendingFile]] = {
    val eitherT: EitherT[F, Error, PendingFile] = for {
      md5                     <- EitherT.right[Error](calculateMd5(file))
      exitingFilesWithSameMd5 <- EitherT.right[Error](findByMd5(userId, md5))
      _                       <- validateExistingFile(exitingFilesWithSameMd5.nonEmpty)
      receiptOption <- EitherT.right[Error](getReceipt(receiptId))
      pendingFile <- receiptOption
        .fold(EitherT.left[PendingFile](Monad[F].pure(ReceiptNotFound(receiptId))): EitherT[F, Error, PendingFile]) (_
          => EitherT.right[Error](submitPF(uploadsLocation, userId, receiptId, file, metadata.fileName)))
    } yield pendingFile

    eitherT.value
  }

  def patchReceipt(receiptId: String, jsonPatch: String): F[Option[ReceiptEntity]] =
    for {
      receiptOption <- getReceipt(receiptId)
      patchedReceipt = receiptOption.map(r => Patch.applyPatch(r, jsonPatch))
      currentTime <- getTime()
      _ <- if (patchedReceipt.isDefined) {
        saveReceipt(receiptId, patchedReceipt.get.copy(lastModified = currentTime))
      } else {
        Monad[F].pure(())
      }
    } yield patchedReceipt

  def findById(receiptId: String): F[Option[ReceiptEntity]] = getReceipt(receiptId)

  private def removeReceiptFiles(userId: String, files: List[FileEntity]): F[List[Unit]] =
    files
      .map(file =>
        for {
          _ <- deleteFile(userId, file.id)
          r <- deleteStoredFile(file.id)
        } yield r)
      .sequence

  def removeReceipt(receiptId: String): F[Option[Unit]] =
    for {
      receiptOption <- getReceipt(receiptId)
      _             <- deleteReceipt(receiptId)
      fileDeletionResult <- if (receiptOption.isDefined) {
        removeReceiptFiles(receiptOption.get.userId, receiptOption.get.files).map(_ => Some(()))
      } else {
        Monad[F].pure(None)
      }
    } yield fileDeletionResult

  def receiptFileWithExtension(receiptId: String, fileId: String): F[Option[FileToServe]] =
    for {
      receiptOption <- getReceipt(receiptId)
      fileToServeOption <- if (receiptOption.isDefined) {
        val extOption = receiptOption.get.files.find(_.id == fileId).map(_.ext)
        fetchFile(receiptOption.get.userId, fileId).map(source => extOption.map(ext => FileToServe(source, ext)))
      } else {
        Monad[F].pure(None)
      }
    } yield fileToServeOption
}