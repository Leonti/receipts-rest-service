package service

import java.io.File
import java.util.concurrent.Executors

import akka.http.scaladsl.server.directives.FileInfo
import cats.free.Free
import freek._
import model._
import ocr.model.OcrTextAnnotation
import ops.FileOps.{FileOp, SubmitPendingFile, SubmitToFileQueue}
import ops.{FileOps, RandomOps, ReceiptOps}
import ops.RandomOps._
import ops.ReceiptOps._
import repository.{OcrRepository, ReceiptRepository}
import routing.ParsedForm
import spray.json._
import gnieh.diffson.sprayJson._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import cats.implicits._

class ReceiptService(receiptRepository: ReceiptRepository, ocrRepository: OcrRepository) {

  implicit val executor: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def findForUserId(userId: String, lastModifiedOption: Option[Long] = None): Future[List[ReceiptEntity]] = {
    receiptRepository
      .findForUserId(userId)
      .map(_.filter(receiptEntity => receiptEntity.lastModified > lastModifiedOption.getOrElse(0l)))
  }

  def createReceipt(userId: String,
                    total: Option[BigDecimal],
                    description: String,
                    transactionTime: Long,
                    tags: List[String]): Future[ReceiptEntity] = {
    val receiptEntity = ReceiptEntity(userId = userId,
                                      total = total,
                                      description = description,
                                      transactionTime = transactionTime,
                                      tags = tags,
                                      files = List())
    receiptRepository.save(receiptEntity)
  }

  def findById(id: String): Future[Option[ReceiptEntity]] =
    receiptRepository.findById(id)

  def save(receiptEntity: ReceiptEntity): Future[ReceiptEntity] =
    receiptRepository
      .save(receiptEntity.copy(lastModified = System.currentTimeMillis()))

  def addFileToReceipt(receiptId: String, file: FileEntity): Future[Option[ReceiptEntity]] =
    receiptRepository.addFileToReceipt(receiptId, file).flatMap(_ => receiptRepository.findById(receiptId))

  def saveOcrResult(userId: String, receiptId: String, ocrResult: OcrTextAnnotation): Future[OcrEntity] =
    ocrRepository.save(OcrEntity(userId = userId, id = receiptId, result = ocrResult))

  def findByText(userId: String, query: String): Future[List[ReceiptEntity]] =
    for {
      ocrTexts: List[OcrTextOnly] <- ocrRepository.findTextOnlyForUserId(userId = userId, query = query)
      receiptIds: List[String] = ocrTexts.map(_.id)
      results <- receiptRepository.findByIds(receiptIds)
    } yield results

  def delete(receiptId: String): Future[Unit] =
    for {
      _ <- receiptRepository.deleteById(receiptId)
      _ <- ocrRepository.deleteById(receiptId)
    } yield Unit
}

object ReceiptService extends JsonProtocols {

  type PRG = ReceiptOp :|: FileOp :|: RandomOp :|: NilDSL
  val PRG = DSL.Make[PRG]

  private def receiptsForQuery(userId: String, query: String): Free[PRG.Cop, Seq[ReceiptEntity]] = {
    for {
      ocrResults <- FindOcrByText(userId, query).freek[PRG]: Free[PRG.Cop, Seq[OcrTextOnly]]
      receiptIds = ocrResults.map(_.id)
      receipts <- GetReceipts(receiptIds).freek[PRG]: Free[PRG.Cop, Seq[ReceiptEntity]]
    } yield receipts
  }

  def findForUser(userId: String, lastModifiedOption: Option[Long], queryOption: Option[String]): Free[PRG.Cop, Seq[ReceiptEntity]] =
    for {

      unfilteredReceipts <- queryOption
        .flatMap(query => if (query.trim.isEmpty) None else Some(query))
        .map(query => receiptsForQuery(userId, query))
        .getOrElse(UserReceipts(userId).freek[PRG]: Free[PRG.Cop, Seq[ReceiptEntity]])

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
      _ <- SubmitToFileQueue(userId, receiptId, file, ext(fileName), pendingFile.id).freek[PRG]
    } yield pendingFile

  def createReceipt(userId: String, parsedForm: ParsedForm): Free[PRG.Cop, ReceiptEntity] =
    for {
      receiptId         <- RandomOps.GenerateGuid().freek[PRG]
      currentTimeMillis <- RandomOps.GetTime().freek[PRG]
      tags         = parsedForm.fields("tags")
      uploadedFile = parsedForm.files("receipt")
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
      _ <- SaveReceipt(receiptId, receipt).freek[PRG]
      _ <- submitPendingFile(userId, receiptId, uploadedFile.file, uploadedFile.fileInfo.fileName)
    } yield receipt

  def addUploadedFileToReceipt(userId: String, receiptId: String, metadata: FileInfo, file: File): Free[PRG.Cop, Option[PendingFile]] =
    for {
      receiptOption <- ReceiptOps.GetReceipt(receiptId).freek[PRG]: Free[PRG.Cop, Option[ReceiptEntity]]
      pendingFileOption <- if (receiptOption.isDefined) {
        submitPendingFile(userId, receiptId, file: File, metadata.fileName).map(pf => Option(pf))
      } else {
        Free.pure[PRG.Cop, Option[PendingFile]](None)
      }
    } yield pendingFileOption

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
      _ <- if (patchedReceipt.isDefined) {
        SaveReceipt(receiptId, patchedReceipt.get).freek[PRG]
      } else {
        Free.pure[PRG.Cop, Unit](())
      }
    } yield patchedReceipt

  def findById(receiptId: String): Free[PRG.Cop, Option[ReceiptEntity]] =
    ReceiptOps.GetReceipt(receiptId).freek[PRG]

  private def removeReceiptFiles(userId: String, files: List[FileEntity]): Free[PRG.Cop, List[Unit]] =
    files.map(file => FileOps.DeleteFile(userId, file.id).freek[PRG]).sequence

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
