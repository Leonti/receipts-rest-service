package service

import model.PendingFile
import repository.PendingFileRepository

import scala.concurrent.Future

class PendingFileService(pendingFileRepository: PendingFileRepository) {

  def save(pendingFile: PendingFile): Future[PendingFile] = pendingFileRepository.save(pendingFile)

  def findForUserId(userId: String): Future[List[PendingFile]] = pendingFileRepository.findForUserId(userId)

  def deleteByReceiptId(receiptId: String): Future[Unit] = pendingFileRepository.deleteByReceiptId(receiptId)

}
