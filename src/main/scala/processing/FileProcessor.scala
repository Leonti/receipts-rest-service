package processing

import java.io.File

import cats.free.Free
import freek._
import model.FileEntity
import ops.FileOps.FileOp
import ops.ReceiptOps.ReceiptOp
import ops.{FileOps, ReceiptOps}
import queue._
import cats.implicits._
import ops.PendingFileOps.PendingFileOp

object FileProcessor {

  type PRG = ReceiptOp :|: FileOp :|: PendingFileOp :|: NilDSL
  val PRG = DSL.Make[PRG]

  def processJob(receiptFileJob: ReceiptFileJob): Free[PRG.Cop, List[QueueJob]] =
    for {
      fileEntities <- FileOps
        .SaveFile(receiptFileJob.userId, new File(receiptFileJob.filePath), receiptFileJob.fileExt)
        .freek[PRG]: Free[PRG.Cop, Seq[FileEntity]]
      _ <- fileEntities.map(fileEntity => ReceiptOps.AddFileToReceipt(receiptFileJob.receiptId, fileEntity).freek[PRG]).toList.sequence
      _ <- FileOps.RemoveFile(new File(receiptFileJob.filePath)).freek[PRG]
    } yield
      List(
        OcrJob(
          userId = receiptFileJob.userId,
          receiptId = receiptFileJob.receiptId,
          fileId = fileEntities.filter(_.parentId.isEmpty).head.id,
          pendingFileId = receiptFileJob.pendingFileId
        ))

}
