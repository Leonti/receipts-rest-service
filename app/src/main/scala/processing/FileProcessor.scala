package processing

import java.io.File

import algebras._
import model.StoredFile
import queue._
import cats.Monad
import cats.implicits._
import scala.language.higherKinds

class FileProcessorTagless[F[_]: Monad](receiptAlg: ReceiptAlg[F], fileAlg: FileAlg[F]) {
  import receiptAlg._, fileAlg._

  def processJob(receiptFileJob: ReceiptFileJob): F[List[QueueJob]] =
    for {
      fileEntities <- saveFile(receiptFileJob.userId, new File(receiptFileJob.filePath), receiptFileJob.fileExt)
      _ <- fileEntities
        .filter(_.md5.isDefined)
        .map(fileEntity => saveStoredFile(StoredFile(receiptFileJob.userId, fileEntity.id, fileEntity.md5.get, fileEntity.metaData.length)))
        .toList
        .sequence
      _ <- fileEntities.map(fileEntity => addFileToReceipt(receiptFileJob.receiptId, fileEntity)).toList.sequence
      _ <- removeFile(new File(receiptFileJob.filePath))
    } yield
      List(
        OcrJob(
          userId = receiptFileJob.userId,
          receiptId = receiptFileJob.receiptId,
          fileId = fileEntities.filter(_.parentId.isEmpty).head.id,
          pendingFileId = receiptFileJob.pendingFileId
        ))

}
