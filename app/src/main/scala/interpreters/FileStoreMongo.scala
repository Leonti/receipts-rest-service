package interpreters
import algebras.FileStoreAlg
import cats.effect.IO
import receipt.StoredFile
import repository.StoredFileRepository
import user.UserId

class FileStoreMongo(storedFileRepository: StoredFileRepository) extends FileStoreAlg[IO] {

  override def saveStoredFile(storedFile: StoredFile): IO[Unit] = IO.fromFuture(IO(storedFileRepository.save(storedFile))).map(_ => ())
  override def findByMd5(userId: UserId, md5: String): IO[Seq[StoredFile]] =
    IO.fromFuture(IO(storedFileRepository.findForUserIdAndMd5(userId.value, md5)))
  override def deleteStoredFile(userId: UserId, id: String): IO[Unit] = IO.fromFuture(IO(storedFileRepository.deleteById(id)))

  def findForUser(userId: UserId): IO[List[StoredFile]] =
    IO.fromFuture(IO(storedFileRepository.findForUserId(userId.value)))

}
