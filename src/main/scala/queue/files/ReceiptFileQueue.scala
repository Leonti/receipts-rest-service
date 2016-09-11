package queue.files

import java.io.File
import java.util.concurrent.Executors

import queue.Models.JobId
import queue.{Queue, ReceiptFileJob}
import service.WebSize

import scala.concurrent.{ExecutionContext, Future}

class ReceiptFileQueue(queue: Queue) {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def submitFile(userId: String, receiptId: String, file: File, fileExt: String): Future[JobId] = {

    queue.put(ReceiptFileJob(
      userId = userId,
      receiptId = receiptId,
      filePath = file.getAbsolutePath,
      fileExt = fileExt
    ))
  }

}
