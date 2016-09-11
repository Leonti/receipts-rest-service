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

    val receiptOption: Future[Option[ReceiptEntity]] = for {
      fileEntities <- fileEntitiesFuture
      receiptResult <- fileEntities.tail.foldLeft(receiptService.addFileToReceipt(receiptFileJob.receiptId, fileEntities.head)) {
        (acc: Future[Option[ReceiptEntity]], next: FileEntity) => {
          acc.flatMap(_ => receiptService.addFileToReceipt(receiptFileJob.receiptId, next))
        }
      }
      _ <- pendingFileService.deleteByReceiptId(receiptFileJob.receiptId)
    } yield receiptResult

    receiptOption.map(_ => ())
  }
}
