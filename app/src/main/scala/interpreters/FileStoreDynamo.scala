package interpreters

import algebras.FileStoreAlg
import cats.effect.IO
import cats.implicits._
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scanamo._
import org.scanamo.error.DynamoReadError
import org.scanamo.syntax._
import org.scanamo.auto._
import receipt.StoredFile
import user.UserId

class FileStoreDynamo(client: AmazonDynamoDBAsync, tableName: String) extends FileStoreAlg[IO] {
  private val table   = Table[StoredFile](tableName)
  private val scanamo = Scanamo(client)

  override def saveStoredFile(storedFile: StoredFile): IO[Unit] = IO {
    val ops = table.putAll(Set(storedFile))
    scanamo.exec(ops)
  }

  override def findByMd5(userId: UserId, md5: String): IO[List[StoredFile]] =
    IO {
      val ops = table
        .index("userId-md5-index")
        .query("userId" -> userId.value and "md5" -> md5)
      val result: Seq[Either[DynamoReadError, StoredFile]] = scanamo.exec(ops)
      result.toList.sequence
    } flatMap {
      case Right(sf)   => IO(sf)
      case Left(error) => IO.raiseError(new Exception(error.toString))
    }

  override def deleteStoredFile(userId: UserId, id: String): IO[Unit] = IO {
    val ops = table.delete("id" -> id and "userId" -> userId.value)
    scanamo.exec(ops)
  }

}
