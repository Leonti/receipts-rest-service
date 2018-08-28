package routing
import io.finch._
import io.finch.syntax._
import model.{User, UserInfo}

class UserEndpoints(auth: Endpoint[User]) {

  val userInfo: Endpoint[UserInfo] = get(auth :: "user" :: "info") { user: User =>
    Ok(UserInfo(id = user.id, userName = user.userName))
  }
}
