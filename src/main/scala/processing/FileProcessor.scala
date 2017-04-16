package processing

import java.io.File
import java.util.concurrent.Executors

import model.{FileEntity, ReceiptEntity}
import ocr.service.OcrService
import queue._
import service.{FileService, PendingFileService, ReceiptService}

import scala.concurrent.{ExecutionContext, Future}

class FileProcessor(
    fileService: FileService,
    ocrService: OcrService,
    receiptService: ReceiptService,
    pendingFileService: PendingFileService
) {

  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def process(receiptFileJob: ReceiptFileJob): Future[Unit] = {

    val fileEntitiesFuture: Future[Seq[FileEntity]] = Future.sequence(
      fileService.save(
        userId = receiptFileJob.userId,
        file = new File(receiptFileJob.filePath),
        ext = receiptFileJob.fileExt
      ))

    val receiptOption: Future[Seq[Option[ReceiptEntity]]] = for {
      fileEntities <- fileEntitiesFuture
      receiptResult <- Future.sequence(fileEntities.map(fileEntity => {
        println(s"Adding file to receipt ${fileEntity}")
        receiptService.addFileToReceipt(receiptFileJob.receiptId, fileEntity)
      }))
      ocrResult <- ocrService.ocrImage(new File(receiptFileJob.filePath))
      _ <- receiptService
        .saveOcrResult(receiptFileJob.userId, receiptFileJob.receiptId, ocrResult)
        .map(result => println(s"Ocr text result ${receiptFileJob.receiptId} ${result.result.text}"))
      _ <- Future { new File(receiptFileJob.filePath).delete() }
      _ <- pendingFileService.deleteById(receiptFileJob.pendingFileId)
    } yield receiptResult

    receiptOption.map(_ => ())
  }
}
