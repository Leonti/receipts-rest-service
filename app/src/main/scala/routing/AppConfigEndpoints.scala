package routing

import io.finch._
import io.finch.syntax._
import model.AppConfig

class AppConfigEndpoints(googleClientId: String) {

  val getAppConfig: Endpoint[AppConfig] = get("config") {
    Ok(AppConfig(googleClientId))
  }

}
