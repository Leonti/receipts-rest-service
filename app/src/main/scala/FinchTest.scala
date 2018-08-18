import algebras.JwtVerificationAlg
import authentication.BearerAuth
import cats.Id
import io.finch.Endpoint
import model.{SubClaim, User}
import cats.instances.future._
import io.finch.syntax.scalaFutures._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
object FinchTest {

  def main(args: Array[String]): Unit = {

    class TestVerificationAlg extends JwtVerificationAlg[Id] {
      override def verify(token: String): Id[Either[String, SubClaim]] = {
        println(s"Verifying token $token")
        Right(SubClaim("authId"))
      }
    }

    val auth: Endpoint[User] = new BearerAuth[Future, User](
      new TestVerificationAlg(),
      _ => Future.successful(Some(User("test", "", List())))
    ).auth

    //   val receiptEndpoints = new ReceiptEndpoints(auth)

    //   Await.ready(Http.server.serve(":8080", receiptEndpoints.test.toService))

  }
}
