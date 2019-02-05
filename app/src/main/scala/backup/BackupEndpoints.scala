package backup

import algebras.TokenAlg
import authentication.OAuth2AccessTokenResponse
import cats.Monad
import fs2.Stream
import cats.effect.{ConcurrentEffect, ContextShift}
import cats.implicits._
import com.twitter.finagle.http.Status
import io.finch._
import model.{SubClaim, User, UserId}

import scala.concurrent.ExecutionContext

class BackupEndpoints[F[_]: Monad](auth: Endpoint[F, User], backupService: BackupService[F], tokenAlg: TokenAlg[F])(
    implicit F: ConcurrentEffect[F],
    cs: ContextShift[F],
    ec: ExecutionContext)
    extends Endpoint.Module[F] {

  val getBackupToken: Endpoint[F, OAuth2AccessTokenResponse] = get(auth :: "backup" :: "token") { user: User =>
    tokenAlg.generatePathToken(s"/user/${user.id}/backup/download").map(Created)
  }

  val downloadBackup: Endpoint[F, Stream[F, Byte]] =
    get("user" :: path[String] :: "backup" :: "download" :: param[String]("access_token")) { (userId: String, accessToken: String) =>
      val out: F[Output[Stream[F, Byte]]] = for {
        eitherClaim <- tokenAlg.verifyPathToken(accessToken)
        output <- eitherClaim match {
          case Right(SubClaim(path)) =>
            if (path == s"/user/$userId/backup/download") {

              backupService.createUserBackup(UserId(userId)).map { receiptsBackup =>
                Ok(receiptsBackup.source)
                  .withHeader("Content-Type", "application/zip")
                  .withHeader("Content-Disposition", s"""attachment; filename="${receiptsBackup.filename}"""")
              }
            } else Monad[F].pure(Output.failure(new Exception("Forbidden"), Status.Forbidden))
          case Left(_) => Monad[F].pure(Output.failure(new Exception("Forbidden"), Status.Forbidden))
        }
      } yield output

      out
    }

  val all = getBackupToken :+: downloadBackup

}
