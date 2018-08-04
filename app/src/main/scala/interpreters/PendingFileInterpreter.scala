package interpreters

import model.PendingFile
import algebras.PendingFileAlg
import repository.PendingFileRepository

import scala.concurrent.Future

class PendingFileInterpreterTagless(pendingFileRepository: PendingFileRepository) extends PendingFileAlg[Future] {
  override def savePendingFile(pendingFile: PendingFile): Future[PendingFile] = pendingFileRepository.save(pendingFile)
  override def findPendingFileForUserId(
      userId: String): Future[List[PendingFile]] = pendingFileRepository.findForUserId(userId)
  override def deletePendingFileById(id: String): Future[Unit] = pendingFileRepository.deleteById(id)
  override def deleteAllPendingFiles(): Future[Unit] = pendingFileRepository.deleteAll()
}
