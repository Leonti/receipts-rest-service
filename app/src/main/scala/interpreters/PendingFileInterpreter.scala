package interpreters

import cats.~>
import model.PendingFile
import ops.PendingFileOps._
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
