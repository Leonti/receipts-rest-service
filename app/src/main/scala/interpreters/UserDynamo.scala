package interpreters

import algebras.UserAlg
import cats.effect.IO
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import model.{AccessToken, ExternalUserInfo}
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.auto._
import service.OpenIdService
import user.UserIds

class UserDynamo(openIdService: OpenIdService, client: AmazonDynamoDBAsync, tableName: String) extends UserAlg[IO] {

  private val table = Table[UserIds](tableName)

  override def findByUsername(username: String): IO[List[UserIds]] = IO {
    val ops    = table.index("username-index").query('username -> username)
    val result = Scanamo.exec(client)(ops)
    result.flatMap(_.toOption)
  }

  override def findByExternalId(id: String): IO[Option[UserIds]] = IO {
    val ops    = table.query('externalId -> id)
    val result = Scanamo.exec(client)(ops)
    result.flatMap(_.toOption).headOption
  }

  override def saveUserIds(userIds: UserIds): IO[Unit] = IO {
    val ops = table.putAll(Set(userIds))
    Scanamo.exec(client)(ops)
  }

  override def getExternalUserInfoFromAccessToken(accessToken: AccessToken): IO[ExternalUserInfo] =
    openIdService.fetchAndValidateTokenInfo(accessToken)
}
