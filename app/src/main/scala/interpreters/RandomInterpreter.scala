package interpreters

import java.io.File

import cats.~>
import ops.RandomOps._
import algebras.RandomAlg

import scala.concurrent.Future

class RandomInterpreter extends (RandomOp ~> Future) {

  def apply[A](i: RandomOp[A]): Future[A] = i match {
    case GenerateGuid() => Future.successful(java.util.UUID.randomUUID.toString)
    case GetTime()      => Future.successful(System.currentTimeMillis())
    case TmpFile()      => Future.successful(File.createTempFile("receipt", "file"))
  }

}

// TODO - no reason to use Future here
class RandomInterpreterTagless extends RandomAlg[Future] {
  override def generateGuid(): Future[String] = Future.successful(java.util.UUID.randomUUID.toString)
  override def getTime(): Future[Long]                = Future.successful(System.currentTimeMillis())
  override def tmpFile(): Future[File]             = Future.successful(File.createTempFile("receipt", "file"))
}
