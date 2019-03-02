/*
import TestInterpreters._
import authentication.BearerAuth
import cats.effect.IO
import io.finch.Input
import model.{SubClaim, User}
import org.scalatest.{FlatSpec, Matchers}
import routing.UserEndpoints
class UserEndpointsSpec extends FlatSpec with Matchers {

  private val USER_ID = "123-user"
  val successfulAuth = new BearerAuth[IO, User](
    new JwtVerificationIntTest(Right(SubClaim(""))),
    _ => IO.pure(Some(User(USER_ID, "email", List())))
  ).auth


  it should "should show user info" in {
    val input = Input.get("/user/info").withHeaders("Authorization" -> "Bearer token")

    val userEndpoints = new UserEndpoints(successfulAuth)

    userEndpoints.userInfo(input).awaitValueUnsafe().map(_.id) shouldBe Some(USER_ID)
  }

}
*/
