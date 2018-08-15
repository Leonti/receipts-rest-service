import TestInterpreters._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import model.{User, UserInfo}
import org.scalatest.{FlatSpec, Matchers}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ContentTypes._
import routing.UserRouting

import scala.concurrent.Future
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import akka.http.scaladsl.model.headers.{HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.{AuthenticationResult, SecurityDirectives}
import service.UserPrograms
import cats.implicits._

class UserRoutingSpec extends FlatSpec with Matchers with ScalatestRouteTest {

  val userInterpreter = new UserInterpreterTagless(List(), "")
  val randomInterpreter = new RandomInterpreterTagless("", 0)
  val tokenInterpreter = new TokenInterpreterTagless(System.currentTimeMillis(), "secret")

  def createAuthentication(user: User) = {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(user))
    }
    SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
  }


  it should "should show user info" in {
    val user = User(id = "123-user", userName = "name", externalIds = List())

    val userRouting = new UserRouting(new UserPrograms(userInterpreter), createAuthentication(user))
    Get("/user/info") ~> userRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[UserInfo] shouldBe UserInfo(user)
    }
  }

}
