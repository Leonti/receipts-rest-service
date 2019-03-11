package interpreters
import algebras.PendingFileAlg
import cats.effect.IO
import pending.PendingFile
import repository.PendingFileRepository
import user.UserId

class PendingFileMongo(pendingFileRepository: PendingFileRepository) extends PendingFileAlg[IO] {
  override def savePendingFile(pendingFile: PendingFile): IO[PendingFile] = IO.fromFuture(IO(pendingFileRepository.save(pendingFile)))
  override def findPendingFileForUserId(userId: UserId): IO[List[PendingFile]] =
    IO.fromFuture(IO(pendingFileRepository.findForUserId(userId.value)))
  override def deletePendingFileById(userId: UserId, id: String): IO[Unit] = IO.fromFuture(IO(pendingFileRepository.deleteById(id)))
}
