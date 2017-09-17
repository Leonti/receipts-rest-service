package interpreters

import cats.~>
import ops.FileOps.FileOp
import ops.OcrOps.OcrOp
import ops.PendingFileOps.PendingFileOp
import ops.RandomOps.RandomOp
import ops.ReceiptOps.ReceiptOp
import ops.TokenOps.TokenOp
import ops.UserOps.UserOp
import ops.EnvOps.EnvOp

import scala.concurrent.Future

case class Interpreters(
    userInterpreter: (UserOp ~> Future),
    tokenInterpreter: (TokenOp ~> Future),
    randomInterpreter: (RandomOp ~> Future),
    fileInterpreter: (FileOp ~> Future),
    receiptInterpreter: (ReceiptOp ~> Future),
    ocrInterpreter: (OcrOp ~> Future),
    pendingFileInterpreter: (PendingFileOp ~> Future),
    envInterpreter: (EnvOp ~> Future)
)
