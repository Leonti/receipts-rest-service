import TestInterpreters._
import cats.Id
import com.twitter.io.Buf
import io.finch.circe._
import io.finch.{Application, Input}
import model.User
import org.scalatest.{FlatSpec, Matchers}
import routing.OauthEndpoints
import service.UserPrograms

class OauthEndpointsSpec extends FlatSpec with Matchers {

  it should "create user info from a token" in {

    val token = """{
                  |    "token": "token"
                  |  }""".stripMargin

    val input = Input.post("/oauth/openid").withBody[Application.Json](Buf.Utf8(token))

    val oauthEndpoints = new OauthEndpoints[Id](new UserPrograms(new UserInterpreterId(Seq(User(
      id = "",
      userName = "",
      externalIds = List("externalId"))), "email"),
      new RandomInterpreterId("userId")
    ))

    oauthEndpoints.validateWithUserCreation(input).awaitValueUnsafe().map(_.id) shouldBe Some("userId")
  }

}
