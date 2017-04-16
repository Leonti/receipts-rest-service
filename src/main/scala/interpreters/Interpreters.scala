package interpreters

import cats.~>
import ops.RandomOps.RandomOp
import ops.TokenOps.TokenOp
import ops.UserOps.UserOp

import scala.concurrent.Future

case class Interpreters(
    userInterpreter: (UserOp ~> Future),
    tokenInterpreter: (TokenOp ~> Future),
    randomInterpreter: (RandomOp ~> Future)
)
