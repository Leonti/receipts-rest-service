package service

import java.io.File

import akka.http.scaladsl.server.directives.FileInfo
import cats.free.Free
import freek._
import model._
import ops.FileOps.{FileOp, MoveFile, SubmitPendingFile, SubmitToFileQueue}
import ops.{FileOps, RandomOps, ReceiptOps}
import ops.RandomOps._
import ops.ReceiptOps._
import ops.EnvOps._
import routing.ParsedForm
import spray.json._
import gnieh.diffson.sprayJson._

import scala.util.Try
import cats.implicits._
import cats.data.EitherT
import ops.OcrOps.{FindIdsByText, OcrOp}

object ReceiptService extends JsonProtocols {

  sealed trait Error
  final case class FileAlreadyExists()                extends Error
  final case class ReceiptNotFound(receiptId: String) extends Error

  type PRG = ReceiptOp :|: FileOp :|: RandomOp :|: OcrOp :|: EnvOp :|: NilDSL
  val PRG = DSL.Make[PRG]

  type ReceiptApp[A] = Free[PRG.Cop, A]

  private def receiptsForQuery(userId: String, query: String): Free[PRG.Cop, Seq[ReceiptEntity]] = {
    for {
      receiptIds <- FindIdsByText(userId, query).freek[PRG]: Free[PRG.Cop, Seq[String]]
      receipts   <- GetReceipts(receiptIds).freek[PRG]: Free[PRG.Cop, Seq[ReceiptEntity]]
    } yield receipts
  }

  def findForUser(userId: String,
                  lastModifiedOption: Option[Long] = None,
                  queryOption: Option[String] = None): Free[PRG.Cop, Seq[ReceiptEntity]] =
    for {

      unfilteredReceipts <- queryOption
        .flatMap(query => if (query.trim.isEmpty) None else Some(query))
        .map(query => receiptsForQuery(userId, query))
        .getOrElse(ReceiptOps.UserReceipts(userId).freek[PRG]: Free[PRG.Cop, Seq[ReceiptEntity]])

      receipts = unfilteredReceipts.filter(receiptEntity => receiptEntity.lastModified > lastModifiedOption.getOrElse(0l))
    } yield receipts

  private def submitPendingFile(userId: String, receiptId: String, file: File, fileName: String): Free[PRG.Cop, PendingFile] =
    for {
      pendingFileId <- RandomOps.GenerateGuid().freek[PRG]
      pendingFile <- SubmitPendingFile(
        PendingFile(
          id = pendingFileId,
          userId = userId,
          receiptId = receiptId
        )
      ).freek[PRG]
      uploadsLocation <- GetEnv("uploadsFolder").freek[PRG]
      filePath        <- Free.pure[PRG.Cop, File](new File(new File(uploadsLocation), pendingFileId))
      _               <- MoveFile(file, filePath).freek[PRG]
      _               <- SubmitToFileQueue(userId, receiptId, filePath, ext(fileName), pendingFile.id).freek[PRG]
    } yield pendingFile

  def validateExistingFile(haveExisting: Boolean): EitherT[ReceiptApp, Error, Unit] =
    if (haveExisting)
      EitherT.left[ReceiptApp, Error, Unit](Free.pure[PRG.Cop, Error](FileAlreadyExists()))
    else
      EitherT.right[ReceiptApp, Error, Unit](Free.pure[PRG.Cop, Unit](()))

  def createReceipt(userId: String, parsedForm: ParsedForm): ReceiptApp[Either[Error, ReceiptEntity]] = {
    val eitherT: EitherT[ReceiptApp, Error, ReceiptEntity] = for {
      md5                     <- EitherT.right[ReceiptApp, Error, String](FileOps.CalculateMd5(parsedForm.files("receipt").file).freek[PRG])
      exitingFilesWithSameMd5 <- EitherT.right[ReceiptApp, Error, Seq[StoredFile]](FileOps.FindByMd5(userId, md5).freek[PRG])
      _                       <- validateExistingFile(exitingFilesWithSameMd5.nonEmpty)
      receiptId               <- EitherT.right[ReceiptApp, Error, String](RandomOps.GenerateGuid().freek[PRG])
      currentTimeMillis       <- EitherT.right[ReceiptApp, Error, Long](RandomOps.GetTime().freek[PRG])
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
      _ <- EitherT.right[ReceiptApp, Error, ReceiptEntity](SaveReceipt(receiptId, receipt).freek[PRG])
      _ <- EitherT.right[ReceiptApp, Error, PendingFile](
        submitPendingFile(userId, receiptId, uploadedFile.file, uploadedFile.fileInfo.fileName))
    } yield receipt

    eitherT.value
  }

  type OptionalReceipt = Option[ReceiptEntity]

  def addUploadedFileToReceipt(userId: String,
                               receiptId: String,
                               metadata: FileInfo,
                               file: File): ReceiptApp[Either[Error, PendingFile]] = {
    val eitherT: EitherT[ReceiptApp, Error, PendingFile] = for {
      md5                     <- EitherT.right[ReceiptApp, Error, String](FileOps.CalculateMd5(file).freek[PRG])
      exitingFilesWithSameMd5 <- EitherT.right[ReceiptApp, Error, Seq[StoredFile]](FileOps.FindByMd5(userId, md5).freek[PRG])
      _                       <- validateExistingFile(exitingFilesWithSameMd5.nonEmpty)
      receiptOption <- EitherT.right[ReceiptApp, Error, OptionalReceipt](
        ReceiptOps.GetReceipt(receiptId).freek[PRG]: Free[PRG.Cop, Option[ReceiptEntity]])
      pendingFile <- if (receiptOption.isDefined) {
        EitherT.right[ReceiptApp, Error, PendingFile](submitPendingFile(userId, receiptId, file, metadata.fileName))
      } else {
        EitherT.left[ReceiptApp, Error, PendingFile](Free.pure[PRG.Cop, Error](ReceiptNotFound(receiptId)))
      }
    } yield pendingFile

    eitherT.value
  }

  private val applyPatch: (ReceiptEntity, String) => ReceiptEntity = (receiptEntity, jsonPatch) => {
    val asJson: String =
      receiptEntity.copy(total = if (receiptEntity.total.isDefined) receiptEntity.total else Some(BigDecimal("0"))).toJson.compactPrint
    val patched: String = JsonPatch.parse(jsonPatch).apply(asJson)
    patched.parseJson.convertTo[ReceiptEntity]
  }

  def patchReceipt(receiptId: String, jsonPatch: String): Free[PRG.Cop, Option[ReceiptEntity]] =
    for {
      receiptOption <- ReceiptOps.GetReceipt(receiptId).freek[PRG]: Free[PRG.Cop, Option[ReceiptEntity]]
      patchedReceipt = receiptOption.map(r => applyPatch(r, jsonPatch))
      currentTime <- GetTime().freek[PRG]
      _ <- if (patchedReceipt.isDefined) {
        SaveReceipt(receiptId, patchedReceipt.get.copy(lastModified = currentTime)).freek[PRG]
      } else {
        Free.pure[PRG.Cop, Unit](())
      }
    } yield patchedReceipt

  def findById(receiptId: String): Free[PRG.Cop, Option[ReceiptEntity]] =
    ReceiptOps.GetReceipt(receiptId).freek[PRG]

  private def removeReceiptFiles(userId: String, files: List[FileEntity]): Free[PRG.Cop, List[Unit]] =
    files
      .map(file =>
        for {
          _ <- FileOps.DeleteFile(userId, file.id).freek[PRG]
          r <- FileOps.DeleteStoredFile(file.id).freek[PRG]
        } yield r)
      .sequence

  def deleteReceipt(receiptId: String): Free[PRG.Cop, Option[Unit]] =
    for {
      receiptOption <- ReceiptOps.GetReceipt(receiptId).freek[PRG]: Free[PRG.Cop, Option[ReceiptEntity]]
      _             <- ReceiptOps.DeleteReceipt(receiptId).freek[PRG]
      fileDeletionResult <- if (receiptOption.isDefined) {
        removeReceiptFiles(receiptOption.get.userId, receiptOption.get.files).map(_ => Some(()))
      } else {
        Free.pure[PRG.Cop, Option[Unit]](None)
      }
    } yield fileDeletionResult

  def receiptFileWithExtension(receiptId: String, fileId: String): Free[PRG.Cop, Option[FileToServe]] =
    for {
      receiptOption <- ReceiptOps.GetReceipt(receiptId).freek[PRG]: Free[PRG.Cop, Option[ReceiptEntity]]
      fileToServeOption <- if (receiptOption.isDefined) {
        val extOption = receiptOption.get.files.find(_.id == fileId).map(_.ext)
        FileOps
          .FetchFile(receiptOption.get.userId, fileId)
          .freek[PRG]
          .map(source => extOption.map(ext => FileToServe(source, ext)))
      } else {
        Free.pure[PRG.Cop, Option[FileToServe]](None)
      }
    } yield fileToServeOption

  private def ext(fileName: String): String = fileName.split("\\.")(1)
}
