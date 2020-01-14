package processing

import algebras._
import model._
import queue._
import cats.Monad
import cats.implicits._
import receipt.{FileEntity, RemoteFileId}
import user.UserId

class FileProcessor[F[_]: Monad](
    receiptAlg: ReceiptStoreAlg[F],
    pendingFileAlg: PendingFileAlg[F],
    localFileAlg: LocalFileAlg[F],
    remoteFileAlg: RemoteFileAlg[F],
    imageAlg: ImageAlg[F],
    randomAlg: RandomAlg[F]
) {

  def processJob(receiptFileJob: ReceiptFileJob): F[List[QueueJob]] =
    for {
      fileStream <- remoteFileAlg.remoteFileStream(receiptFileJob.remoteFileId)
      tmpFile    <- randomAlg.tmpFile()
      _          <- localFileAlg.streamToFile(fileStream, tmpFile)
      timestamp  <- randomAlg.getTime()
      isImage    <- imageAlg.isImage(tmpFile)
      _ <- if (isImage) {
        for {
          originalMetadata <- imageAlg.getImageMetaData(tmpFile)
          resizedFile      <- imageAlg.resizeToPixelSize(tmpFile, WebSize.pixels)
          resizedMetadata  <- imageAlg.getImageMetaData(resizedFile)
          resizedFileId    <- randomAlg.generateGuid()
          _                <- remoteFileAlg.saveRemoteFile(resizedFile, RemoteFileId(UserId(receiptFileJob.userId), resizedFileId))
          _                <- localFileAlg.removeFile(resizedFile)
          _ <- receiptAlg.addFileToReceipt(
            UserId(receiptFileJob.userId),
            receiptFileJob.receiptId,
            FileEntity(
              id = receiptFileJob.remoteFileId.fileId,
              parentId = None,
              ext = receiptFileJob.fileExt,
              metaData = originalMetadata,
              timestamp = timestamp
            )
          )
          _ <- receiptAlg.addFileToReceipt(
            UserId(receiptFileJob.userId),
            receiptFileJob.receiptId,
            FileEntity(
              id = resizedFileId,
              parentId = Some(receiptFileJob.remoteFileId.fileId),
              ext = receiptFileJob.fileExt,
              metaData = resizedMetadata,
              timestamp = timestamp
            )
          )
        } yield ()
      } else
        for {
          fileMetaData <- localFileAlg.getGenericMetaData(tmpFile)
          _ <- receiptAlg.addFileToReceipt(
            UserId(receiptFileJob.userId),
            receiptFileJob.receiptId,
            FileEntity(
              id = receiptFileJob.remoteFileId.fileId,
              parentId = None,
              ext = receiptFileJob.fileExt,
              metaData = fileMetaData,
              timestamp = timestamp
            )
          )
          _ <- pendingFileAlg.deletePendingFileById(UserId(receiptFileJob.userId), receiptFileJob.pendingFileId)
        } yield ()
      _              <- localFileAlg.removeFile(tmpFile)
      updatedReceipt <- receiptAlg.getReceipt(UserId(receiptFileJob.userId), receiptFileJob.receiptId)
    } yield updatedReceipt
      .map { receipt =>
        if (isImage)
          List(
            OcrJob(
              userId = receiptFileJob.userId,
              receiptId = receiptFileJob.receiptId,
              fileId = receipt.files.filter(_.parentId.isEmpty).head.id,
              pendingFileId = receiptFileJob.pendingFileId
            )
          )
        else List()
      }
      .getOrElse(List())

}
