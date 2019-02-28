package routing
import cats.Monad
import cats.effect.Effect
import model.{User, UserInfo}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe.CirceEntityEncoder._

class UserEndpoints[F[_]: Effect]() {

  val authedRoutes: AuthedService[User, F] = AuthedService {
    case GET -> Root / "user" / "info" as user =>
      Monad[F].pure(Response(status = Status.Ok).withEntity(UserInfo(id = user.id, userName = user.userName)))
  }
}
