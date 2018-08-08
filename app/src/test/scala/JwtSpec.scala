import interpreters.JwtVerificationInterpreter
import model.{SubClaim, User}
import org.scalatest.{FlatSpec, Matchers}
import service.JwtTokenGenerator

class JwtSpec extends FlatSpec with Matchers {

  val user = User(
    id = "some_id",
    userName = "testUsername"
  )

  private val tokenSecret = "secret".getBytes

  it should "encode and decode user token" in {

    val now = System.currentTimeMillis() // FIXME - make sure verifier gets time passed in

    val tokenResponse = JwtTokenGenerator.generateToken(user, now, tokenSecret)
    val verifier = new JwtVerificationInterpreter(tokenSecret)

    val tokenResult: Either[String, SubClaim] = verifier.verify(tokenResponse.accessToken)

    tokenResult shouldBe Right(SubClaim(user.id))
  }

}
