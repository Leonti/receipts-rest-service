package processing

import model.OcrText
import queue._
import cats.Monad
import cats.implicits._
import algebras._

import scala.language.higherKinds

class OcrProcessorTagless[F[_]: Monad](fileAlg: FileAlg[F],
                                       ocrAlg: OcrAlg[F],
                                       randomAlg: RandomAlg[F],
                                       pendingFileAlg: PendingFileAlg[F]) {
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
