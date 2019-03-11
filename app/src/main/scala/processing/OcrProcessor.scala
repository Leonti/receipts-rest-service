package processing

import queue._
import cats.Monad
import cats.implicits._
import algebras._
import ocr.OcrText
import receipt.RemoteFileId
import user.UserId

import scala.language.higherKinds

class OcrProcessor[F[_]: Monad](remoteFileAlg: RemoteFileAlg[F],
                                localFileAlg: LocalFileAlg[F],
                                ocrAlg: OcrAlg[F],
                                randomAlg: RandomAlg[F],
                                pendingFileAlg: PendingFileAlg[F]) {
  import ocrAlg._, randomAlg._, pendingFileAlg._

  def processJob(ocrJob: OcrJob): F[List[QueueJob]] =
    for {
      fileSource <- remoteFileAlg.remoteFileStream(RemoteFileId(UserId(ocrJob.userId), ocrJob.fileId))
      tmpFile    <- tmpFile()
      _          <- localFileAlg.streamToFile(fileSource, tmpFile)
      ocrResult  <- ocrImage(tmpFile)
      _          <- saveOcrResult(ocrJob.userId, ocrJob.receiptId, ocrResult)
      _          <- addOcrToIndex(ocrJob.userId, ocrJob.receiptId, OcrText(ocrResult.text))
      _          <- localFileAlg.removeFile(tmpFile)
      _          <- deletePendingFileById(UserId(ocrJob.userId), ocrJob.pendingFileId)
    } yield List()
}
