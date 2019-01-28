
import cats.effect.{ContextShift, IO}
import io.circe.generic.auto._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class TestSpec extends FlatSpec with Matchers with ScalaFutures {
  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(120, Seconds), interval = Span(1000, Millis))

  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  val (httpClient, _) = BlazeClientBuilder[IO](global)
    .withResponseHeaderTimeout(60.seconds)
    .withRequestTimeout(60.seconds)
    .resource.allocated.unsafeRunSync()

  it should "run a test spec" in {

    val token: IO[Auth0TokenResponse] = httpClient.expect[Auth0TokenResponse](
      POST(
        Auth0TokenRequest(
          client_id = "test",
          client_secret = "test",
          audience = "test"
        ),
        Uri.uri("https://leonti.au.auth0.com/oauth/token")
      )
    )

    val futureToken = token.unsafeToFuture()


    whenReady(futureToken) { t =>
      t shouldBe 1
    }
  }

}
