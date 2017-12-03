package interpreters

import cats.~>
import com.typesafe.config.Config
import ops.EnvOps._

import scala.concurrent.Future

class EnvInterpreter(config: Config) extends (EnvOp ~> Future) {

  def apply[A](i: EnvOp[A]): Future[A] = i match {
    case GetEnv(key: String) => Future.successful(config.getString(key))
  }
}
