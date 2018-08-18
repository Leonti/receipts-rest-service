import algebras.JwtVerificationAlg
import authentication.BearerAuth
import cats.Id
//import com.twitter.finagle.Http
//import com.twitter.util.Await
import io.finch.Endpoint
import model.{SubClaim, User}
//import routing.ReceiptEndpoints
//import routing.ExceptionEncoders._
//import io.finch.circe._
//import io.circe.generic.auto._

import scala.concurrent.Future
object FinchTest {


  def main(args: Array[String]): Unit = {

    class TestVerificationAlg extends JwtVerificationAlg[Id] {
      override def verify(token: String): Id[Either[String, SubClaim]] = {
        println(s"Verifying token $token")
        Right(SubClaim("authId"))
      }
    }

    val auth: Endpoint[User] = new BearerAuth(
      new TestVerificationAlg(),
      _ => Future.successful(Some(User("test", "", List())))
    ).auth

 //   val receiptEndpoints = new ReceiptEndpoints(auth)

 //   Await.ready(Http.server.serve(":8080", receiptEndpoints.test.toService))

  }
}
