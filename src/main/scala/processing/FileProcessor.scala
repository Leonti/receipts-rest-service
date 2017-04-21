package processing

import java.io.File

import cats.free.Free
import freek._
import model.FileEntity
import ops.FileOps.FileOp
import ops.ReceiptOps.ReceiptOp
import ops.{FileOps, OcrOps, PendingFileOps, ReceiptOps}
import queue._
import cats.implicits._
import ops.OcrOps.OcrOp
import ops.PendingFileOps.PendingFileOp

object FileProcessor {

  type PRG = ReceiptOp :|: FileOp :|: OcrOp :|: PendingFileOp :|: NilDSL
  val PRG = DSL.Make[PRG]

  def processJob(receiptFileJob: ReceiptFileJob): Free[PRG.Cop, Unit] =
    for {
      fileEntities <- FileOps
        .SaveFile(receiptFileJob.userId, new File(receiptFileJob.filePath), receiptFileJob.fileExt)
        .freek[PRG]: Free[PRG.Cop, Seq[FileEntity]]
      _                 <- fileEntities.map(fileEntity => ReceiptOps.AddFileToReceipt(receiptFileJob.receiptId, fileEntity).freek[PRG]).toList.sequence
      ocrResult         <- OcrOps.OcrImage(new File(receiptFileJob.filePath)).freek[PRG]
      _                 <- OcrOps.SaveOcrResult(receiptFileJob.userId, receiptFileJob.receiptId, ocrResult).freek[PRG]
      _                 <- FileOps.RemoveFile(new File(receiptFileJob.filePath)).freek[PRG]
      pendingFileResult <- PendingFileOps.DeletePendingFileById(receiptFileJob.pendingFileId).freek[PRG]
    } yield pendingFileResult

}
