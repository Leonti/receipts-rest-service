package authentication
import algebras.JwtVerificationAlg
import io.catbird.util.Rerunnable
import io.finch.{Endpoint, EndpointResult, Input, Output, Trace}
import model.SubClaim
import authentication.FutureConverters._
import cats.Id
import com.twitter.finagle.http.Status

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class BearerAuth[U](verificationAlg: JwtVerificationAlg[Id],
                       fromBearerTokenClaim: SubClaim => Future[Option[U]]
                      ) {
  import verificationAlg._

  private val REGEXP_AUTHORIZATION = """^\s*(OAuth|Bearer)\s+([^\s\,]*)""".r

  val auth: Endpoint[U] = (input: Input) => {

    val result = input.request.authorization.flatMap(header => REGEXP_AUTHORIZATION.findFirstMatchIn(header).map(_.group(2))) match {
      case Some(token) => verify(token) match {
        case Right(subClaim) => fromBearerTokenClaim(subClaim).map {
          case Some(a) => Output.payload(a)
          case None => Output.failure(new Exception("User doesn't exist"), Status.Unauthorized)
        }.asTwitter
        case Left(_) => Future.successful(Output.failure(new Exception("Verification failed for access token"), Status.Unauthorized)).asTwitter
      }
      case None => Future.successful(Output.failure(new Exception("Invalid bearer token"), Status.Unauthorized)).asTwitter
    }

    EndpointResult.Matched(input, Trace.empty, Rerunnable.fromFuture(result))
  }

}
