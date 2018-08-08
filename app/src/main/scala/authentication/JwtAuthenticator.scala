package authentication

import akka.http.scaladsl.model.headers.{HttpCookiePair, _}
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1}
import akka.http.scaladsl.server.directives.BasicDirectives._
import akka.http.scaladsl.server.directives.FutureDirectives._
import akka.http.scaladsl.server.directives.RouteDirectives._
import akka.http.scaladsl.server.directives.CookieDirectives._
import akka.http.scaladsl.server.directives._
import akka.pattern.after
import algebras.JwtVerificationAlg
import cats.Id
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Success
import model.SubClaim

class JwtAuthenticator[Auth](verificationAlg: JwtVerificationAlg[Id],
                             realm: String,
                             fromUsernamePassword: (String, String) => Future[Option[Auth]],
                             fromBearerTokenClaim: SubClaim => Future[Option[Auth]])(implicit executor: ExecutionContext)
    extends SecurityDirectives {
  import verificationAlg._

  def apply(): Directive1[Auth] = {
    bearerTokenOrCookie(acceptExpired = false).recover { rejs =>
      basic().recover(rejs2 => reject(rejs ++ rejs2: _*))
    }
  }

  def basic(minDelay: Option[FiniteDuration] = None): AuthenticationDirective[Auth] = {
    authenticateOrRejectWithChallenge[BasicHttpCredentials, Auth] {
      case Some(BasicHttpCredentials(username, password)) =>
        val auth = fromUsernamePassword(username, password)
        val delayedAuth = minDelay match {
          case None => auth
          case Some(delay) =>
            val delayed = after[Option[Auth]](delay, SimpleScheduler.instance)(Future(None))

            val promise = Promise[Option[Auth]]()
            auth.onComplete {
              case Success(Some(user)) => promise.success(Some(user))
              case _                   => delayed.onComplete(_ => promise.success(None))
            }
            promise.future
        }

        delayedAuth map {
          case Some(user) => grant(user)
          case None       => deny
        }

      case None => Future(deny)
    }
  }

  def bearerTokenOrCookie(acceptExpired: Boolean = false): AuthenticationDirective[Auth] = {
    def resolve(subClaim: SubClaim): Future[AuthenticationResult[Auth]] = fromBearerTokenClaim(subClaim).map {
      case Some(user) => grant(user)
      case None       => deny(None)
    }

    val authenticator: Option[String] => Future[AuthenticationResult[Auth]] = {
      case Some(tokenStr) =>
        verify(tokenStr) match {
          case Right(subClaim)                          => resolve(subClaim)
          case Left(error)                           => Future(deny(Some(error)))
        }
      case None => Future(deny(Some("Missing token")))
    }

    extractExecutionContext.flatMap { implicit ec =>
      optionalCookie("access_token").flatMap { (cookieTokenOption: Option[HttpCookiePair]) =>
        extractCredentials.flatMap { (credOption: Option[HttpCredentials]) =>
          {

            val cookieToken = cookieTokenOption.map(_.value)
            val credToken   = credOption.map(_.token())
            val tokenOption = credToken.orElse(cookieToken)

            onSuccess(authenticator(tokenOption)).flatMap {
              case Right(user) =>
                setCookie(
                  HttpCookie.fromPair(
                    pair = HttpCookiePair("access_token", tokenOption.get),
                    path = Some("/"),
                    httpOnly = true
                  )).tflatMap((Unit) => provide(user))
              case Left(challenge) =>
                val cause = if (tokenOption.isEmpty) CredentialsMissing else CredentialsRejected
                reject(AuthenticationFailedRejection(cause, challenge)): Directive1[Auth]
            }

          }
        }
      }
    }
  }

  private def grant(user: Auth)          = AuthenticationResult.success(user)
  private def deny                       = AuthenticationResult.failWithChallenge(createBasicChallenge)
  private def deny(error: Option[String]) = AuthenticationResult.failWithChallenge(createBearerTokenChallenge(error))

  private def createBasicChallenge: HttpChallenge = {
    HttpChallenge("Basic", realm)
  }

  private def createBearerTokenChallenge(error: Option[String]): HttpChallenge = {
    val desc = error match {
      case None                             => None
      case Some(message)                    => Some(message)
    }
    val params = desc match {
      case Some(msg) => Map("error" -> "invalid_token", "error_description" -> msg)
      case None      => Map.empty[String, String]
    }
    HttpChallenge("Bearer", realm, params)
  }
}
