package interpreters
import algebras.ReceiptStoreAlg
import cats.effect.IO
import receipt.{FileEntity, ReceiptEntity}
import user.UserId
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.auto._
import org.scanamo.error.{DynamoReadError, NoPropertyOfType}
import cats.implicits._
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import org.scanamo.query.{MultipleKeyList, UniqueKeys}

class ReceiptsStoreDynamo(client: AmazonDynamoDBAsync, tableName: String) extends ReceiptStoreAlg[IO] {

  implicit val stringFormat: DynamoFormat[String] = new DynamoFormat[String] {
    def read(av: AttributeValue): Either[DynamoReadError, String] =
      Either.fromOption(Option(av.getS), NoPropertyOfType("S", av)).map {
        case "DYNAMO_BUG" => ""
        case s => s
      }
    def write(s: String): AttributeValue = s match {
      case "" => new AttributeValue().withS("DYNAMO_BUG")
      case _  => new AttributeValue().withS(s)
    }
  }

  private val table = Table[ReceiptEntity](tableName)

  override def getReceipt(userId: UserId, id: String): IO[Option[ReceiptEntity]] = IO {
    val ops    = table.get('id -> id and 'userId -> userId.value)
    val result = Scanamo.exec(client)(ops)
    result.flatMap(_.toOption)
  }

  override def deleteReceipt(userId: UserId, id: String): IO[Unit] = IO {
    val ops = table.delete('id -> id and 'userId -> userId.value)
    Scanamo.exec(client)(ops)
  }

  override def saveReceipt(receipt: ReceiptEntity): IO[ReceiptEntity] = IO {
    val ops = table.putAll(Set(receipt))
    Scanamo.exec(client)(ops)
    receipt
  }

  override def getReceipts(userId: UserId, ids: Seq[String]): IO[Seq[ReceiptEntity]] =
    IO {
      val values                                              = ids.map(id => (id, userId.value)).toSet
      val ops                                                 = table.getAll(UniqueKeys(MultipleKeyList(('id, 'userId), values)))
      val result: Set[Either[DynamoReadError, ReceiptEntity]] = Scanamo.exec(client)(ops)
      result.toList.sequence
    } flatMap {
      case Right(receipts) => IO(receipts)
      case Left(error)     => IO.raiseError(new Exception(error.toString))
    }

  override def userReceipts(userId: UserId): IO[Seq[ReceiptEntity]] =
    IO {
      val ops                                                 = table.index("userId-index").query('userId -> userId.value)
      val result: Seq[Either[DynamoReadError, ReceiptEntity]] = Scanamo.exec(client)(ops)
      result.toList.sequence
    } flatMap {
      case Right(receipts) => IO(receipts)
      case Left(error)     => IO.raiseError(new Exception(error.toString))
    }

  override def addFileToReceipt(userId: UserId, receiptId: String, file: FileEntity): IO[Unit] = IO {
    val ops = table.update('id -> receiptId and 'userId -> userId.value, append('files -> file))
    Scanamo.exec(client)(ops)
  }
}
