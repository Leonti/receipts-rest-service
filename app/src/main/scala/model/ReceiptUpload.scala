package model
import fs2.Stream

case class ReceiptField[F[_]](name: String, stream: Stream[F, Byte])
case class ReceiptUpload[F[_]](receipt: ReceiptField[F],
                               total: Option[BigDecimal],
                               description: String,
                               transactionTime: Long,
                               tags: List[String])
