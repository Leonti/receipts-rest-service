package routing

import cats.Monad
import cats.effect.Effect
import io.finch._

class VersionEndpoint[F[_]: Monad](v: String)(implicit F: Effect[F]) extends Endpoint.Module[F] {

  val version: Endpoint[F, String] = get("version") { Ok(v) }

}
