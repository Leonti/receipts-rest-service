package authentication
import algebras.JwtVerificationAlg
import cats.data.{Kleisli, OptionT}
import cats.effect.Effect
import cats.{Id, Monad}
import cats.implicits._
import org.http4s._
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import user.User

import scala.language.higherKinds

class BearerAuth[F[_]: Effect](verificationAlg: JwtVerificationAlg[Id], fromBearerTokenClaim: SubClaim => F[Option[User]]) {
  import verificationAlg._

  private val REGEXP_AUTHORIZATION = """^\s*(OAuth|Bearer)\s+([^\s\,]*)""".r

  private val retrieveUser: Kleisli[F, SubClaim, Either[String, User]] =
    Kleisli(subClaim => fromBearerTokenClaim(subClaim).map(_.toRight("User doesn't exist")))

  private val authUser: Kleisli[F, Request[F], Either[String, User]] =
    Kleisli({ req =>
      val tokenFromHeader =
        req.headers.get(Authorization).flatMap(header => REGEXP_AUTHORIZATION.findFirstMatchIn(header.value).map(_.group(2)))
      val tokenFromCookie = headers.Cookie.from(req.headers).flatMap(_.values.toList.find(_.name == "access_token").map(_.content))

      val token: Either[String, String]     = tokenFromHeader.orElse(tokenFromCookie).toRight("Invalid bearer token")
      val message: Either[String, SubClaim] = token.flatMap(token => verify(token))

      val traversed: F[Either[String, User]] = message.flatTraverse(retrieveUser.run)

      traversed
    })

  private val onFailure: AuthedService[String, F] = Kleisli(
    req => OptionT.liftF(Monad[F].pure(Response(status = Status.Unauthorized).withEntity(req.authInfo))))

  val authMiddleware: AuthMiddleware[F, User] = AuthMiddleware(authUser, onFailure)

}
