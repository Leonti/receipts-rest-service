package interpreters
import algebras.FileStoreAlg
import cats.effect.IO
import model.StoredFile
import repository.StoredFileRepository

class FileStoreMongo(storedFileRepository: StoredFileRepository) extends FileStoreAlg[IO] {

  override def saveStoredFile(storedFile: StoredFile): IO[Unit] = IO.fromFuture(IO(storedFileRepository.save(storedFile))).map(_ => ())
  override def findByMd5(userId: String, md5: String): IO[Seq[StoredFile]] =
    IO.fromFuture(IO(storedFileRepository.findForUserIdAndMd5(userId, md5)))
  override def deleteStoredFile(storedFileId: String): IO[Unit] = IO.fromFuture(IO(storedFileRepository.deleteById(storedFileId)))
}
