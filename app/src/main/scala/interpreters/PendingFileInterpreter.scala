package interpreters

import cats.~>
import model.PendingFile
import ops.PendingFileOps._
import algebras.PendingFileAlg
import repository.PendingFileRepository

import scala.concurrent.Future

class PendingFileInterpreter(pendingFileRepository: PendingFileRepository) extends (PendingFileOp ~> Future) {

  def apply[A](i: PendingFileOp[A]): Future[A] = i match {
    case SavePendingFile(pendingFile: PendingFile) => pendingFileRepository.save(pendingFile)
    case FindPendingFileForUserId(userId: String)  => pendingFileRepository.findForUserId(userId)
    case DeletePendingFileById(id: String)         => pendingFileRepository.deleteById(id)
    case DeleteAllPendingFiles()                   => pendingFileRepository.deleteAll()
  }

}

class PendingFileInterpreterTagless(pendingFileRepository: PendingFileRepository) extends PendingFileAlg[Future] {
  override def savePendingFile(pendingFile: PendingFile): Future[PendingFile] = pendingFileRepository.save(pendingFile)
  override def findPendingFileForUserId(
      userId: String): Future[List[PendingFile]] = pendingFileRepository.findForUserId(userId)
  override def deletePendingFileById(id: String): Future[Unit] = pendingFileRepository.deleteById(id)
  override def deleteAllPendingFiles(): Future[Unit] = pendingFileRepository.deleteAll()
}
