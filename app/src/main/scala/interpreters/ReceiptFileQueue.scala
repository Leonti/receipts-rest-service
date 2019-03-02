package interpreters

import java.util.concurrent.Executors

import algebras.QueueAlg
import cats.effect.IO
import queue.Models.JobId
import queue.{Queue, ReceiptFileJob}
import receipt.RemoteFileId

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class ReceiptFileQueue(queue: Queue) extends QueueAlg[IO] {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  override def submitToFileQueue(userId: String,
                                 receiptId: String,
                                 remoteFileId: RemoteFileId,
                                 fileExt: String,
                                 pendingFileId: String): IO[JobId] =
    queue.put(
      ReceiptFileJob(
        userId = userId,
        receiptId = receiptId,
        remoteFileId = remoteFileId,
        fileExt = fileExt,
        pendingFileId = pendingFileId
      ))
}
