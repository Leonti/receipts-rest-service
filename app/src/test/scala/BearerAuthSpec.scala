import TestInterpreters.TestVerificationAlg
import authentication.BearerAuth
import cats.Id
import com.twitter.finagle.http.Status
import io.finch._
import model.{SubClaim, User}
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import instances.identity._

class BearerAuthSpec extends FlatSpec with Matchers with ScalaFutures {

  val successfulAuth: Endpoint[User] = new BearerAuth[Id, User](
    new TestVerificationAlg(Right(SubClaim(""))),
    _ => Some(User("id", "email", List()))
  ).auth

  it should "extract and validate token successfully" in {
    val input = Input.get("").withHeaders("Authorization" -> "Bearer token")

    successfulAuth(input).awaitValueUnsafe().isDefined shouldBe true
  }

  it should "fail with invalid header" in {
    val input = Input.get("").withHeaders("Authorization" -> "Bearertoken")

    successfulAuth(input).awaitOutputUnsafe().map(_.status) shouldBe Some(Status.Unauthorized)
  }

  it should "fail when header is not present" in {
    val input = Input.get("").withHeaders("Authorization" -> "Bearertoken")

    successfulAuth(input).awaitOutputUnsafe().map(_.status) shouldBe Some(Status.Unauthorized)
  }

  it should "when verification fails" in {
    val failedVerification: Endpoint[User] = new BearerAuth[Id, User](
      new TestVerificationAlg(Left("Failed to verify token")),
      _ => Some(User("id", "email", List()))
    ).auth

    val input = Input.get("").withHeaders("Authorization" -> "Bearer token")

    failedVerification(input).awaitOutputUnsafe().map(_.status) shouldBe Some(Status.Unauthorized)
  }

  it should "when user is not found" in {
    val userNotFoundAuth: Endpoint[User] = new BearerAuth[Id, User](
      new TestVerificationAlg(Right(SubClaim(""))),
      _ => None
    ).auth

    val input = Input.get("").withHeaders("Authorization" -> "Bearer token")

    userNotFoundAuth(input).awaitOutputUnsafe().map(_.status) shouldBe Some(Status.Unauthorized)
  }
}
