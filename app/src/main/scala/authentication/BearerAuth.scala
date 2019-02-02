package authentication
import algebras.JwtVerificationAlg
import cats.effect.Effect
import io.finch.{Endpoint, EndpointResult, Input, Output, Trace}
import model.SubClaim
import cats.{Id, Monad}
import com.twitter.finagle.http.Status
import cats.implicits._

import scala.language.higherKinds

class BearerAuth[F[_]: Monad, U](verificationAlg: JwtVerificationAlg[Id], fromBearerTokenClaim: SubClaim => F[Option[U]])
                                (implicit F: Effect[F]) extends Endpoint.Module[F]{
  import verificationAlg._

  private val REGEXP_AUTHORIZATION = """^\s*(OAuth|Bearer)\s+([^\s\,]*)""".r

  val auth: Endpoint[F, U] = (input: Input) => {

    val tokenFromHeader = input.request.authorization.flatMap(header => REGEXP_AUTHORIZATION.findFirstMatchIn(header).map(_.group(2)))
    val tokenFormCookie = input.request.cookies.getValue("access_token")
    
    val result: F[Output[U]] =
      tokenFromHeader.orElse(tokenFormCookie) match {
        case Some(token) =>
          verify(token) match {
            case Right(subClaim) =>
              fromBearerTokenClaim(subClaim).map {
                case Some(a) => Output.payload(a)
                case None    => Output.failure(new Exception("User doesn't exist"), Status.Unauthorized)
              }
            case Left(_) => Monad[F].pure(Output.failure(new Exception("Verification failed for access token"), Status.Unauthorized))
          }
        case None => Monad[F].pure(Output.failure(new Exception("Invalid bearer token"), Status.Unauthorized))
      }

    EndpointResult.Matched(input, Trace.empty, result)
  }

}
