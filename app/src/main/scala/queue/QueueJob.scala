package queue

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser._
import io.circe.syntax._
import receipt.RemoteFileId

trait QueueJob {
  def context: String
}

case class ContextHolder(context: String)

object ContextHolder {
  implicit val contextHolderDecoder: Decoder[ContextHolder] = deriveDecoder
  implicit val contextHolderEncoder: Encoder[ContextHolder] = deriveEncoder
}

case class ReceiptFileJob(
    userId: String,
    receiptId: String,
    remoteFileId: RemoteFileId,
    fileExt: String,
    pendingFileId: String,
    context: String = "RECEIPT_FILE"
) extends QueueJob

object ReceiptFileJob {
  implicit val receiptFileJobDecoder: Decoder[ReceiptFileJob] = deriveDecoder
  implicit val receiptFileJobEncoder: Encoder[ReceiptFileJob] = deriveEncoder
}

case class OcrJob(userId: String, receiptId: String, fileId: String, pendingFileId: String, context: String = "RECEIPT_OCR")
    extends QueueJob

object OcrJob {
  implicit val ocrJobDecoder: Decoder[OcrJob] = deriveDecoder
  implicit val ocrJobEncoder: Encoder[OcrJob] = deriveEncoder
}

case class ReservedJob(id: String, job: QueueJob)

object QueueJob {

  def fromString(asString: String): QueueJob = {
    val contextHolder = decode[ContextHolder](asString)

    contextHolder.toOption.get.context match { // FIXME - don't use right.get
      case "RECEIPT_FILE" => decode[ReceiptFileJob](asString).toOption.get
      case "RECEIPT_OCR"  => decode[OcrJob](asString).toOption.get
    }
  }

  def asString(queueJob: QueueJob): String = queueJob match {
    case receiptFileJob: ReceiptFileJob => receiptFileJob.asJson.spaces2
    case ocrJob: OcrJob                 => ocrJob.asJson.spaces2
  }
}
