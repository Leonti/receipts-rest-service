package interpreters
import algebras.PendingFileAlg
import cats.effect.IO
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.auto._
import org.scanamo.error.DynamoReadError
import cats.implicits._
import pending.PendingFile
import user.UserId

class PendingFileStoreDynamo(client: AmazonDynamoDBAsync, tableName: String) extends PendingFileAlg[IO] {
  private val table = Table[PendingFile](tableName)

  override def savePendingFile(pendingFile: PendingFile): IO[PendingFile] = IO {
    val ops = table.putAll(Set(pendingFile))
    Scanamo.exec(client)(ops)
    pendingFile
  }

  override def findPendingFileForUserId(userId: UserId): IO[List[PendingFile]] =
    IO {
      val ops                                               = table.index("userId-index").query('userId -> userId.value)
      val result: Seq[Either[DynamoReadError, PendingFile]] = Scanamo.exec(client)(ops)
      result.toList.sequence
    } flatMap {
      case Right(pf)   => IO(pf)
      case Left(error) => IO.raiseError(new Exception(error.toString))
    }

  override def deletePendingFileById(userId: UserId, id: String): IO[Unit] = IO {
    val ops = table.delete('id -> id and 'userId -> userId.value)
    Scanamo.exec(client)(ops)
  }
}
