package queue

import model.PendingFile.PendingFileId
import queue.Models.JobId
import spray.json._

trait QueueJob {
  def context: String
}

case class ContextHolder(context: String)

case class ReceiptFileJob(userId: String,
                          receiptId: String,
                          filePath: String,
                          fileExt: String,
                          pendingFileId: PendingFileId,
                          context: String = "RECEIPT_FILE")
    extends QueueJob

case class OcrJob(userId: String, receiptId: String, fileId: String, pendingFileId: PendingFileId, context: String = "RECEIPT_OCR")
    extends QueueJob

case class ReservedJob(id: JobId, job: QueueJob)

object QueueJob extends DefaultJsonProtocol with NullOptions {

  implicit val contextHolderFormat  = jsonFormat1(ContextHolder.apply)
  implicit val receiptFileJobFormat = jsonFormat6(ReceiptFileJob.apply)
  implicit val ocrJobFormat         = jsonFormat5(OcrJob)

  def fromString(asString: String): QueueJob = {
    val contextHolder = asString.parseJson.convertTo[ContextHolder]

    contextHolder.context match {
      case "RECEIPT_FILE" => asString.parseJson.convertTo[ReceiptFileJob]
      case "RECEIPT_OCR"  => asString.parseJson.convertTo[OcrJob]
    }
  }

  def asString(queueJob: QueueJob): String = queueJob match {
    case (receiptFileJob: ReceiptFileJob) => receiptFileJob.toJson.prettyPrint
    case (ocrJob: OcrJob)                 => ocrJob.toJson.prettyPrint
  }
}