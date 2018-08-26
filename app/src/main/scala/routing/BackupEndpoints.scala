package routing

import algebras.TokenAlg
import authentication.OAuth2AccessTokenResponse
import cats.Monad
import cats.implicits._
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.Status
import com.twitter.io.{Buf, Reader}
import io.finch._
import io.finch.syntax._
import model.{SubClaim, User, UserId}
import service.BackupServiceIO
import com.twitter.conversions.storage._

class BackupEndpoints[F[_]: ToTwitterFuture : Monad](auth: Endpoint[User], backupService: BackupServiceIO[F], tokenAlg: TokenAlg[F]) {

  val getBackupToken: Endpoint[OAuth2AccessTokenResponse] = get(auth :: "backup" :: "token") { user: User =>
    tokenAlg.generatePathToken(s"/user/${user.id}/backup/download").map(Created)
  }

  val downloadBackup: Endpoint[AsyncStream[Buf]] = get("user" :: path[String] :: "backup" :: "download" :: param[String]("access_token")) { (userId: String, accessToken: String) =>

    val out: F[Output[AsyncStream[Buf]]] = for {
      eitherClaim <- tokenAlg.verifyPathToken(accessToken)
      output <- eitherClaim match {
        case Right(SubClaim(path)) => if (path == s"/user/$userId/backup/download") {

          backupService.createUserBackup(UserId(userId)).map { receiptsBackup =>
            receiptsBackup.runSource.unsafeRunAsync(r => println(r))
            println("Serving backup stream")

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

  val all = getBackupToken :+: downloadBackup

}
