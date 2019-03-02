import TestInterpreters._
import cats.effect.{ContextShift, IO}
import org.http4s.headers.Authorization
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.scalatest.{FlatSpec, Matchers}
import routing.Routing
import user.UserInfo

import scala.concurrent.ExecutionContext.Implicits.global

class UserSpec extends FlatSpec with Matchers {
  private implicit val cs: ContextShift[IO] = IO.contextShift(global)

  val accessToken = "token"
  val authHeader = Authorization(Credentials.Token(AuthScheme.Bearer, accessToken))

  it should "should show user info" in {
    val routing = new Routing(testAlgebras, testConfig)

    val request: Request[IO] = Request(
      method = Method.GET,
      uri = Uri.uri("/user/info"),
      headers = Headers(authHeader)
    )

    val userInfo = routing.routes.run(request).value.unsafeRunSync().map(_.as[UserInfo].unsafeRunSync())

    userInfo.map(_.id) shouldBe Some(defaultUserId)
  }

  it should "fail with invalid header" in {
    val routing = new Routing(testAlgebras, testConfig)

    val request: Request[IO] = Request(
      method = Method.GET,
      uri = Uri.uri("/user/info"),
      headers = Headers(Header("Authorization", "Bear"))
    )

    val status = routing.routes.run(request).value.unsafeRunSync().map(_.status)

    status shouldBe Some(Status.Unauthorized)
  }

  it should "fail when user doesn't exist" in {
    val routing = new Routing(testAlgebras.copy(
      userAlg = new UserIntTest(users = List())
    ), testConfig)

    val request: Request[IO] = Request(
      method = Method.GET,
      uri = Uri.uri("/user/info"),
      headers = Headers(authHeader)
    )

    val status = routing.routes.run(request).value.unsafeRunSync().map(_.status)

    status shouldBe Some(Status.Unauthorized)
  }

}

