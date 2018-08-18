package authentication
import algebras.JwtVerificationAlg
import io.catbird.util.Rerunnable
import io.finch.{Endpoint, EndpointResult, Input, Output, Trace}
import model.SubClaim
import cats.{Id, Monad}
import com.twitter.finagle.http.Status
import io.finch.syntax.ToTwitterFuture

import cats.implicits._

import scala.language.higherKinds

class BearerAuth[F[_]: Monad, U](verificationAlg: JwtVerificationAlg[Id],
                       fromBearerTokenClaim: SubClaim => F[Option[U]]
                      )(implicit ttf: ToTwitterFuture[F]) {
  import verificationAlg._

  private val REGEXP_AUTHORIZATION = """^\s*(OAuth|Bearer)\s+([^\s\,]*)""".r

  val auth: Endpoint[U] = (input: Input) => {

    val result: F[Output[U]] = input.request.authorization.flatMap(header => REGEXP_AUTHORIZATION.findFirstMatchIn(header).map(_.group(2))) match {
      case Some(token) => verify(token) match {
        case Right(subClaim) => fromBearerTokenClaim(subClaim).map {
          case Some(a) => Output.payload(a)
          case None => Output.failure(new Exception("User doesn't exist"), Status.Unauthorized)
        }
        case Left(_) => Monad[F].pure(Output.failure(new Exception("Verification failed for access token"), Status.Unauthorized))
      }
      case None => Monad[F].pure(Output.failure(new Exception("Invalid bearer token"), Status.Unauthorized))
    }

    EndpointResult.Matched(input, Trace.empty, Rerunnable.fromFuture(ttf(result)))
  }

}
