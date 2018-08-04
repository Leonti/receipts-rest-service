package processing

import cats.free.Free
import freek._
import model.OcrText
import ops.FileOps.FileOp
import ops.ReceiptOps.ReceiptOp
import ops.RandomOps.RandomOp
import ops._
import queue._
import ops.OcrOps.{AddOcrToIndex, OcrOp}
import ops.PendingFileOps.PendingFileOp
import cats.Monad
import cats.implicits._
import algebras._

import scala.language.higherKinds

class OcrProcessorTagless[F[_]: Monad](fileAlg: FileAlg[F], ocrAlg: OcrAlg[F], randomAlg: RandomAlg[F], pendingFileAlg: PendingFileAlg[F]) {
  import fileAlg._, ocrAlg._, randomAlg._, pendingFileAlg._

  def processJob(ocrJob: OcrJob): F[List[QueueJob]] =
    for {
      fileSource <- fetchFile(ocrJob.userId, ocrJob.fileId)
      tmpFile    <- tmpFile()
      _          <- sourceToFile(fileSource, tmpFile)
      ocrResult  <- ocrImage(tmpFile)
      _          <- saveOcrResult(ocrJob.userId, ocrJob.receiptId, ocrResult)
      _          <- addOcrToIndex(ocrJob.userId, ocrJob.receiptId, OcrText(ocrResult.text))
      _          <- removeFile(tmpFile)
      _          <- deletePendingFileById(ocrJob.pendingFileId)
    } yield List()
}

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
      _          <- AddOcrToIndex(ocrJob.userId, ocrJob.receiptId, OcrText(ocrResult.text)).freek[PRG]
      _          <- FileOps.RemoveFile(tmpFile).freek[PRG]
      _          <- PendingFileOps.DeletePendingFileById(ocrJob.pendingFileId).freek[PRG]
    } yield List()

}
