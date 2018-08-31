package processing

import java.io.File
import java.util.concurrent.Executors

import cats.effect.IO
import model._
import queue.files.ReceiptFileQueue
import service.PendingFileService

import scala.concurrent.ExecutionContext

class ReceiptFiles(pendingFileService: PendingFileService, receiptFileQueue: ReceiptFileQueue) {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def submitFile(userId: String, receiptId: String, file: File, fileExt: String): IO[PendingFile] = {

    val pendingFileFuture = for {
      pendingFile <- IO.fromFuture(
        IO(
          pendingFileService.save(
            PendingFile(
              id = java.util.UUID.randomUUID().toString,
              userId = userId,
              receiptId = receiptId
            ))))
      _ <- receiptFileQueue.submitFile(userId, receiptId, file, fileExt, pendingFile.id)
    } yield pendingFile

    pendingFileFuture
  }

}
