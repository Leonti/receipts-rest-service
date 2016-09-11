package processing

import java.io.File
import java.util.concurrent.Executors

import model._
import queue.files.ReceiptFileQueue
import service.PendingFileService

import scala.concurrent.{ExecutionContext, Future}

class ReceiptFiles(pendingFileService: PendingFileService, receiptFileQueue: ReceiptFileQueue) {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def submitFile(userId: String, receiptId: String, file: File, fileExt: String): Future[PendingFile] = {

    val pendingFileFuture = for {
      pendingFile <- pendingFileService.save(PendingFile(
        id = java.util.UUID.randomUUID().toString,
        userId = userId,
        receiptId = receiptId
      ))
      _ <- receiptFileQueue.submitFile(userId, receiptId, file, fileExt)
    } yield pendingFile

    pendingFileFuture
  }


}
