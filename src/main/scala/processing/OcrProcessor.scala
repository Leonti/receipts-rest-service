package processing

import cats.free.Free
import freek._
import ops.FileOps.FileOp
import ops.ReceiptOps.ReceiptOp
import ops.RandomOps.RandomOp
import ops._
import queue._
import ops.OcrOps.OcrOp
import ops.PendingFileOps.PendingFileOp

object OcrProcessor {

  type PRG = ReceiptOp :|: FileOp :|: OcrOp :|: RandomOp :|: PendingFileOp :|: NilDSL
  val PRG = DSL.Make[PRG]

  def processJob(ocrJob: OcrJob): Free[PRG.Cop, List[QueueJob]] =
    for {
      fileSource <- FileOps.FetchFile(ocrJob.userId, ocrJob.fileId).freek[PRG]
      tmpFile    <- RandomOps.TmpFile().freek[PRG]
      _          <- FileOps.SourceToFile(fileSource, tmpFile).freek[PRG]
      ocrResult  <- OcrOps.OcrImage(tmpFile).freek[PRG]
      _          <- OcrOps.SaveOcrResult(ocrJob.userId, ocrJob.receiptId, ocrResult).freek[PRG]
      _          <- FileOps.RemoveFile(tmpFile).freek[PRG]
      _          <- PendingFileOps.DeletePendingFileById(ocrJob.pendingFileId).freek[PRG]
    } yield List()

}
