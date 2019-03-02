package interpreters
import algebras.PendingFileAlg
import cats.effect.IO
import pending.PendingFile
import repository.PendingFileRepository

class PendingFileMongo(pendingFileRepository: PendingFileRepository) extends PendingFileAlg[IO] {
  override def savePendingFile(pendingFile: PendingFile): IO[PendingFile] = IO.fromFuture(IO(pendingFileRepository.save(pendingFile)))
  override def findPendingFileForUserId(userId: String): IO[List[PendingFile]] =
    IO.fromFuture(IO(pendingFileRepository.findForUserId(userId)))
  override def deletePendingFileById(id: String): IO[Unit] = IO.fromFuture(IO(pendingFileRepository.deleteById(id)))
  override def deleteAllPendingFiles(): IO[Unit]           = IO.fromFuture(IO(pendingFileRepository.deleteAll()))
}
