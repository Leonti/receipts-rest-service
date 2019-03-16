package pending

import algebras.PendingFileAlg
import cats.effect.Effect
import cats.implicits._
import org.http4s._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.io._
import user.{UserId, UserIds}

class PendingFileEndpoints[F[_]: Effect](pendingFileAlg: PendingFileAlg[F]) {

  val authedRoutes: AuthedService[UserIds, F] = AuthedService {
    case GET -> Root / "pending-file" as user =>
      pendingFileAlg.findPendingFileForUserId(UserId(user.id)).map(pf => Response(status = Status.Created).withEntity(pf))
  }

}
