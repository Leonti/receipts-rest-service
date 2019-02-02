package backup

import algebras.TokenAlg
import authentication.OAuth2AccessTokenResponse
import cats.Monad
import cats.effect.Effect
import cats.implicits._
import io.finch._
import model.User

class BackupEndpoints[F[_]: Monad](auth: Endpoint[F, User], /*backupService: BackupService[F],*/ tokenAlg: TokenAlg[F])
                                  (implicit F: Effect[F]) extends Endpoint.Module[F]{

  val getBackupToken: Endpoint[F, OAuth2AccessTokenResponse] = get(auth :: "backup" :: "token") { user: User =>
    tokenAlg.generatePathToken(s"/user/${user.id}/backup/download").map(Created)
  }
/*
  val downloadBackup: Endpoint[AsyncStream[Buf]] = get("user" :: path[String] :: "backup" :: "download" :: param[String]("access_token")) {
    (userId: String, accessToken: String) =>
      val out: F[Output[AsyncStream[Buf]]] = for {
        eitherClaim <- tokenAlg.verifyPathToken(accessToken)
        output <- eitherClaim match {
          case Right(SubClaim(path)) =>
            if (path == s"/user/$userId/backup/download") {

              backupService.createUserBackup(UserId(userId)).map { receiptsBackup =>
                receiptsBackup.runSource.unsafeRunAsync(r => println(r))

                Ok(AsyncStream.fromReader(Reader.fromStream(receiptsBackup.source), chunkSize = 128.kilobytes.inBytes.toInt))
                  .withHeader("Content-Type", "application/zip")
                  .withHeader("Content-Disposition", s"""attachment; filename="${receiptsBackup.filename}"""")
              }
            } else Monad[F].pure(Output.failure(new Exception("Forbidden"), Status.Forbidden))
          case Left(_) => Monad[F].pure(Output.failure(new Exception("Forbidden"), Status.Forbidden))
        }
      } yield output

      out
  }
*/

  val all = getBackupToken //:+: downloadBackup

}
