package routing

import io.finch._
import io.finch.syntax._

class VersionEndpoint(v: String) {

  val version: Endpoint[String] = get("version") { Ok(v) }

}
