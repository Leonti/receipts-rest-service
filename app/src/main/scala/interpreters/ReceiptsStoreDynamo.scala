package interpreters
import algebras.ReceiptStoreAlg
import cats.effect.IO
//import com.amazonaws.services.dynamodbv2.model.BatchWriteItemResult

//import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
//import com.amazonaws.services.dynamodbv2.model.BatchWriteItemResult
import model.{FileEntity, ReceiptEntity, UserId}

//import org.scanamo._
//import org.scanamo.syntax._

//import com.gu.scanamo._
//import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
//import io.github.howardjohn.scanamo.CirceDynamoFormat._

class ReceiptsStoreDynamo extends ReceiptStoreAlg[IO] {
  override def getReceipt(userId: UserId,
                          id: String): IO[Option[ReceiptEntity]] = ???
  /*
    IO {

    val table = Table[ReceiptEntity]("receipts")
    val ops = table.putAll(Set(ReceiptEntity(
      id = java.util.UUID.randomUUID.toString,
      userId = "",
      files= List.empty,
      description = "hello",
      total= None,
      timestamp = System.currentTimeMillis,
      lastModified = System.currentTimeMillis(),
      transactionTime = System.currentTimeMillis(),
      tags = List.empty
    )))

    val client = LocalDynamoDB.client()
    val result: Seq[BatchWriteItemResult] = Scanamo.exec(client)(ops)
    println(result)
    None
  }
  */
  override def deleteReceipt(userId: UserId, id: String): IO[Unit]            = ???
  override def saveReceipt(userId: UserId,
                             id: String,
                             receipt: ReceiptEntity): IO[ReceiptEntity] = ???
  override def getReceipts(userId: UserId,
                             ids: Seq[String]): IO[Seq[ReceiptEntity]] = ???
    /*
    IO {

    case class Farmer(name: String, age: Long)
    val table = Table[Farmer]("receipts")
    val ops = table.get('name -> "McDonald")
    val client = LocalDynamoDB.client()
    val result: Option[Either[DynamoReadError, Farmer]] = Scanamo.exec(client)(ops)
    println(result)
    Seq()
    // table.get('name -> "McDonald")
  }
  */
  override def userReceipts(userId: UserId): IO[Seq[ReceiptEntity]] = ???
  override def addFileToReceipt(userId: UserId,
                                  receiptId: String,
                                  file: FileEntity): IO[Unit] = ???
}
