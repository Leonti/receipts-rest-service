package processing

import algebras._
import model._
import queue._
import cats.Monad
import cats.implicits._

import scala.language.higherKinds

class FileProcessor[F[_]: Monad](
    receiptAlg: ReceiptAlg[F],
    localFileAlg: LocalFileAlg[F],
    remoteFileAlg: RemoteFileAlg[F],
    imageResizeAlg: ImageResizeAlg[F],
    randomAlg: RandomAlg[F]
) {

  def processJob(receiptFileJob: ReceiptFileJob): F[List[QueueJob]] =
    for {
      fileStream   <- remoteFileAlg.fetchRemoteFileInputStream(receiptFileJob.remoteFileId)
      tmpFile      <- randomAlg.tmpFile()
      _            <- localFileAlg.streamToFile(fileStream, tmpFile)
      fileMetaData <- localFileAlg.getFileMetaData(tmpFile)
      _ <- fileMetaData match {
        case _: ImageMetaData =>
          for {
            resizedFile   <- imageResizeAlg.resizeToPixelSize(tmpFile, WebSize.pixels)
            resizedFileId <- randomAlg.generateGuid()
            timestamp     <- randomAlg.getTime()
            _             <- remoteFileAlg.saveRemoteFile(resizedFile, RemoteFileId(UserId(receiptFileJob.userId), resizedFileId))
            _             <- localFileAlg.removeFile(resizedFile)
            _ <- receiptAlg.addFileToReceipt(
              UserId(receiptFileJob.userId),
              receiptFileJob.receiptId,
              FileEntity(
                id = resizedFileId,
                parentId = None,
                ext = receiptFileJob.fileExt,
                metaData = fileMetaData,
                timestamp = timestamp
              )
            )
          } yield ()
        case _ => Monad[F].pure(())
      }
      _              <- localFileAlg.removeFile(tmpFile)
      updatedReceipt <- receiptAlg.getReceipt(UserId(receiptFileJob.userId), receiptFileJob.receiptId)
    } yield
      updatedReceipt
        .map { receipt =>
          List(
            OcrJob(
              userId = receiptFileJob.userId,
              receiptId = receiptFileJob.receiptId,
              fileId = receipt.files.filter(_.parentId.isEmpty).head.id,
              pendingFileId = receiptFileJob.pendingFileId
            ))
        }
        .getOrElse(List())

}
