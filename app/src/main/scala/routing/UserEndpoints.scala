package routing
import cats.Monad
import cats.effect.Effect
import io.finch._
import model.{User, UserInfo}

class UserEndpoints[F[_]: Monad](auth: Endpoint[F, User])(implicit F: Effect[F]) extends Endpoint.Module[F] {

  val userInfo: Endpoint[F, UserInfo] = get(auth :: "user" :: "info") { user: User =>
    Ok(UserInfo(id = user.id, userName = user.userName))
  }
}
