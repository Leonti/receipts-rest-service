package model
import java.io.File

case class ReceiptUpload(
    receipt: File,
    fileName: String,
    total: Option[BigDecimal],
    description: String,
    transactionTime: Long,
    tags: List[String]
)
