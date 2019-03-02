import TestInterpreters._
import cats.effect.{ContextShift, IO}
import io.circe.Json
import org.http4s._
import org.http4s.circe._
import org.http4s.circe.CirceEntityDecoder._
import org.scalatest.{FlatSpec, Matchers}
import routing.Routing
import user.UserInfo

import scala.concurrent.ExecutionContext.Implicits.global

class OauthEndpointsSpec extends FlatSpec with Matchers {
  private implicit val cs: ContextShift[IO] = IO.contextShift(global)

  it should "create user info from a token" in {
    val routing = new Routing(
      testAlgebras.copy(randomAlg = new RandomIntTest("userId")),
      testConfig)

    val token = Json.obj(
      "token" -> Json.fromString("token")
    )

    val request: Request[IO] = Request(
      method = Method.POST,
      uri = Uri.uri("/oauth/openid"),
      body = EntityEncoder[IO, Json].toEntity(token).body
    )

    val userInfo = routing.routes.run(request).value.unsafeRunSync().map(_.as[UserInfo].unsafeRunSync())

    userInfo.map(_.id) shouldBe Some("userId")
  }

}
