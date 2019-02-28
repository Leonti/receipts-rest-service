package routing

import cats.Monad
import cats.effect.Effect
import org.http4s._
import org.http4s.dsl.io._

class VersionEndpoint[F[_]: Effect](v: String) {

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "version" => Monad[F].pure(Response(status = Status.Ok).withEntity(v))
  }

}
