package backup

import authentication.{PathToken, SubClaim}
import cats.Monad
import cats.effect.{ConcurrentEffect, ContextShift}
import cats.implicits._
import org.http4s.{HttpRoutes, _}
import org.http4s.dsl.io._
import org.http4s.circe._
import io.circe.syntax._
import user.{UserId, UserIds}

class BackupEndpoints[F[_]: Monad: ConcurrentEffect: ContextShift](backupService: BackupService[F], pathToken: PathToken) {

  val authedRoutes: AuthedRoutes[UserIds, F] = AuthedRoutes.of {
    case GET -> Root / "backup" / "token" as user =>
      val token = pathToken.generatePathToken(s"/user/${user.id}/backup/download")
      Monad[F].pure(Response(status = Status.Ok).withEntity(token.asJson))

  }

  private object AccessTokenParamMatcher extends QueryParamDecoderMatcher[String]("access_token")

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "user" / userId / "backup" / "download" :? AccessTokenParamMatcher(accessToken) =>
      pathToken.verifyPathToken(accessToken) match {
        case Right(SubClaim(path)) =>
          if (path == s"/user/$userId/backup/download") {
            backupService.createUserBackup(UserId(userId)).map { receiptsBackup =>
              Response(status = Status.Ok)
                .withBodyStream(receiptsBackup.source)
                .withHeaders(
                  Header("Content-Type", "application/zip"),
                  Header("Content-Disposition", s"""attachment; filename="${receiptsBackup.filename}"""")
                )
            }
          } else Monad[F].pure(Response(status = Status.Forbidden).withEmptyBody): F[Response[F]]
        case Left(_) => Monad[F].pure(Response(status = Status.Forbidden).withEmptyBody): F[Response[F]]
      }
  }

}
