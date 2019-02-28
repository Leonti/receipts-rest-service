package routing

import algebras.PendingFileAlg
import cats.effect.Effect
import model.User
import cats.implicits._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe.CirceEntityEncoder._

class PendingFileEndpoints[F[_]: Effect](pendingFileAlg: PendingFileAlg[F]) {

  val authedRoutes: AuthedService[User, F] = AuthedService {
    case GET -> Root / "pending-file" as user =>
      pendingFileAlg.findPendingFileForUserId(user.id).map(pf => Response(status = Status.Created).withEntity(pf))
  }

}
