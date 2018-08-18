import TestInterpreters.TestVerificationAlg
import authentication.BearerAuth
import com.twitter.finagle.http.Status
import io.finch._
import model.{SubClaim, User}
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future
class BearerAuthSpec extends FlatSpec with Matchers with ScalaFutures {

  val successfulAuth: Endpoint[User] = new BearerAuth(
    new TestVerificationAlg(Right(SubClaim(""))),
    _ => Future.successful(Some(User("id", "email", List())))
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
    val failedVerification: Endpoint[User] = new BearerAuth(
      new TestVerificationAlg(Left("Failed to verify token")),
      _ => Future.successful(Some(User("id", "email", List())))
    ).auth

    val input = Input.get("").withHeaders("Authorization" -> "Bearer token")

    failedVerification(input).awaitOutputUnsafe().map(_.status) shouldBe Some(Status.Unauthorized)
  }

  it should "when user is not found" in {
    val userNotFoundAuth: Endpoint[User] = new BearerAuth(
      new TestVerificationAlg(Right(SubClaim(""))),
      _ => Future.successful(None)
    ).auth

    val input = Input.get("").withHeaders("Authorization" -> "Bearer token")

    userNotFoundAuth(input).awaitOutputUnsafe().map(_.status) shouldBe Some(Status.Unauthorized)
  }
}
