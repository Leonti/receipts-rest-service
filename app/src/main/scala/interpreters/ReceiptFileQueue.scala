package queue.files

import java.util.concurrent.Executors

import cats.effect.IO
import model.PendingFile.PendingFileId
import model.RemoteFileId
import queue.Models.JobId
import queue.{Queue, ReceiptFileJob}

import scala.concurrent.ExecutionContext

class ReceiptFileQueue(queue: Queue) {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def submitFile(
      userId: String,
      receiptId: String,
      remoteFileId: RemoteFileId,
      fileExt: String,
      pendingFileId: PendingFileId
  ): IO[JobId] = {

    queue.put(
      ReceiptFileJob(
        userId = userId,
        receiptId = receiptId,
        remoteFileId = remoteFileId,
        fileExt = fileExt,
        pendingFileId = pendingFileId
      ))
  }

}
