package interpreters
import java.io.{File, InputStream}
import java.util.concurrent.Executors

import algebras.RemoteFileAlg
import cats.effect.IO
import model.RemoteFileId

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class RemoteFileInterpreter(s3Region: String, s3Bucket: String) extends RemoteFileAlg[IO] {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  override def saveRemoteFile(file: File, fileId: RemoteFileId): IO[Unit]        = ???
  override def fetchRemoteFileInputStream(fileId: RemoteFileId): IO[InputStream] = ???
  override def deleteRemoteFile(fileId: RemoteFileId): IO[Unit]                                 = ???
}
