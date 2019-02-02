package routing

import cats.Monad
import cats.effect.Effect
import io.finch._
import model.AppConfig

class AppConfigEndpoints[F[_]: Monad](googleClientId: String)(implicit F: Effect[F]) extends Endpoint.Module[F] {

  val getAppConfig: Endpoint[F, AppConfig] = get("config") {
    Ok(AppConfig(googleClientId))
  }

}
