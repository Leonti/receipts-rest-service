package interpreters

import java.io.File
import algebras.RandomAlg

import scala.concurrent.Future

// TODO - no reason to use Future here
class RandomInterpreterTagless extends RandomAlg[Future] {
  override def generateGuid(): Future[String] = Future.successful(java.util.UUID.randomUUID.toString)
  override def getTime(): Future[Long]        = Future.successful(System.currentTimeMillis())
  override def tmpFile(): Future[File]        = Future.successful(File.createTempFile("receipt", "file"))
}
