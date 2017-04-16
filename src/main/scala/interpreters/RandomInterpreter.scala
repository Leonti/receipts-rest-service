package interpreters

import cats.~>
import ops.RandomOps.{GenerateGuid, RandomOp}

import scala.concurrent.Future

class RandomInterpreter extends (RandomOp ~> Future) {

  def apply[A](i: RandomOp[A]): Future[A] = i match {
    case GenerateGuid() => Future.successful(java.util.UUID.randomUUID.toString)
  }

}
