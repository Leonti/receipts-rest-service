package queue.files

import java.io.File
import java.util.concurrent.Executors

import cats.effect.IO
import model.PendingFile.PendingFileId
import queue.Models.JobId
import queue.{Queue, ReceiptFileJob}

import scala.concurrent.ExecutionContext

class ReceiptFileQueue(queue: Queue) {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def submitFile(
      userId: String,
      receiptId: String,
      file: File,
      fileExt: String,
      pendingFileId: PendingFileId
  ): IO[JobId] = {

    queue.put(
      ReceiptFileJob(
        userId = userId,
        receiptId = receiptId,
        filePath = file.getAbsolutePath,
        fileExt = fileExt,
        pendingFileId = pendingFileId
      ))
  }

}
