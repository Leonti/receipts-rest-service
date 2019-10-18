package interpreters
import algebras.ReceiptStoreAlg
import cats.effect.IO
import receipt.{FileEntity, ReceiptEntity}
import user.UserId
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.auto._
import org.scanamo.error.DynamoReadError
import cats.implicits._
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scanamo.query.{MultipleKeyList, UniqueKeys}

class ReceiptsStoreDynamo(client: AmazonDynamoDBAsync, tableName: String) extends ReceiptStoreAlg[IO] {
  private val scanamo = Scanamo(client)
  private val table   = Table[ReceiptEntity](tableName)

  override def getReceipt(userId: UserId, id: String): IO[Option[ReceiptEntity]] = IO {
    val ops    = table.get("id" -> id and "userId" -> userId.value)
    val result = scanamo.exec(ops)
    result.flatMap(_.toOption)
  }

  override def deleteReceipt(userId: UserId, id: String): IO[Unit] = IO {
    val ops = table.delete("id" -> id and "userId" -> userId.value)
    scanamo.exec(ops)
  }

  override def saveReceipt(receipt: ReceiptEntity): IO[ReceiptEntity] = IO {
    val ops = table.putAll(Set(receipt))
    scanamo.exec(ops)
    receipt
  }

  override def getReceipts(userId: UserId, ids: List[String]): IO[List[ReceiptEntity]] =
    IO {
      val values                                              = ids.map(id => (id, userId.value)).toSet
      val ops                                                 = table.getAll(UniqueKeys(MultipleKeyList(("id", "userId"), values)))
      val result: Set[Either[DynamoReadError, ReceiptEntity]] = scanamo.exec(ops)
      result.toList.sequence
    } flatMap {
      case Right(receipts) => IO(receipts)
      case Left(error)     => IO.raiseError(new Exception(error.toString))
    }

  override def userReceipts(userId: UserId): IO[List[ReceiptEntity]] =
    IO {
      val ops = table
        .index("userId-lastModified-index")
        .query("userId" -> userId.value)
      val result: List[Either[DynamoReadError, ReceiptEntity]] = scanamo.exec(ops)
      result.sequence
    } flatMap {
      case Right(receipts) => IO(receipts)
      case Left(error)     => IO.raiseError(new Exception(error.toString))
    }

  override def recentUserReceipts(userId: UserId, lastModified: Long): IO[List[ReceiptEntity]] =
    IO {
      val ops = table
        .index("userId-lastModified-index")
        .query("userId" -> userId.value and ("lastModified" > lastModified))
      val result: List[Either[DynamoReadError, ReceiptEntity]] = scanamo.exec(ops)
      result.sequence
    } flatMap {
      case Right(receipts) => IO(receipts)
      case Left(error)     => IO.raiseError(new Exception(error.toString))
    }

  override def addFileToReceipt(userId: UserId, receiptId: String, file: FileEntity): IO[Unit] = IO {
    val ops = table.update("id" -> receiptId and "userId" -> userId.value, append("files" -> file))
    scanamo.exec(ops)
  }
}
