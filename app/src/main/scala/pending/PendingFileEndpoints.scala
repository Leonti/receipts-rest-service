package pending

import algebras.PendingFileAlg
import cats.effect.Effect
import cats.implicits._
import org.http4s._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.io._
import user.User

class PendingFileEndpoints[F[_]: Effect](pendingFileAlg: PendingFileAlg[F]) {

  val authedRoutes: AuthedService[User, F] = AuthedService {
    case GET -> Root / "pending-file" as user =>
      pendingFileAlg.findPendingFileForUserId(user.id).map(pf => Response(status = Status.Created).withEntity(pf))
  }

}
