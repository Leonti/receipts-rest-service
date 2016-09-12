package processing


import java.io.File
import java.util.concurrent.Executors

import model.{FileEntity, ReceiptEntity}
import queue._
import repository.PendingFileRepository
import service.{FileService, PendingFileService, ReceiptService}

import scala.concurrent.{ExecutionContext, Future}

class FileProcessor(
                     fileService: FileService,
                     receiptService: ReceiptService,
                     pendingFileService: PendingFileService
                   ) {

  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def process(receiptFileJob: ReceiptFileJob): Future[Unit] = {
    val fileEntitiesFuture: Future[Seq[FileEntity]] = Future.sequence(fileService.save(
      userId = receiptFileJob.userId,
      file = new File(receiptFileJob.filePath),
      ext = receiptFileJob.fileExt
    )).map(result => {
      new File(receiptFileJob.filePath).delete()
      result
    })

    val receiptOption: Future[Seq[Option[ReceiptEntity]]] = for {
      fileEntities <- fileEntitiesFuture
      receiptResult <- Future.sequence(fileEntities.map(fileEntity => {
        println(s"Adding file to receipt ${fileEntity}")
        receiptService.addFileToReceipt(receiptFileJob.receiptId, fileEntity)
      }
      ))
      _ <- pendingFileService.deleteById(receiptFileJob.pendingFileId)
    } yield receiptResult

    receiptOption.map(_ => ())
  }
}
