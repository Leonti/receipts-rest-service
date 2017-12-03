package interpreters

import java.io.File

import cats.~>
import ops.RandomOps._

import scala.concurrent.Future

class RandomInterpreter extends (RandomOp ~> Future) {

  def apply[A](i: RandomOp[A]): Future[A] = i match {
    case GenerateGuid() => Future.successful(java.util.UUID.randomUUID.toString)
    case GetTime()      => Future.successful(System.currentTimeMillis())
    case TmpFile()      => Future.successful(File.createTempFile("receipt", "file"))
  }

}