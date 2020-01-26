package routing

import cats.Monad
import cats.effect.Effect
import model.PublicAppConfig
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe.CirceEntityEncoder._

class AppConfigEndpoints[F[_]: Effect](googleClientId: String) {

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "config" => Monad[F].pure(Response(status = Status.Ok).withEntity(PublicAppConfig(googleClientId)))
  }

}
