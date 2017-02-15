package authentication

import akka.http.scaladsl.model.DateTime
import akka.http.scaladsl.model.headers.{HttpCookiePair, _}
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1}
import akka.http.scaladsl.server.directives.BasicDirectives._
import akka.http.scaladsl.server.directives.FutureDirectives._
import akka.http.scaladsl.server.directives.RouteDirectives._
import akka.http.scaladsl.server.directives.CookieDirectives._
import akka.http.scaladsl.server.directives._
import akka.pattern.after
import de.choffmeister.auth.common.JsonWebToken
import de.choffmeister.auth.common.JsonWebToken._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Success

class JwtAuthenticator[Auth](
                           realm: String,
                           bearerTokenSecret: Array[Byte],
                           fromUsernamePassword: (String, String) => Future[Option[Auth]],
                           fromBearerToken: JsonWebToken => Future[Option[Auth]])(implicit executor: ExecutionContext) extends SecurityDirectives {
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
              case _ => delayed.onComplete(_ => promise.success(None))
            }
            promise.future
        }

        delayedAuth map {
          case Some(user) => grant(user)
          case None => deny
        }

      case None => Future(deny)
    }
  }

  def bearerTokenOrCookie(acceptExpired: Boolean = false): AuthenticationDirective[Auth]= {
    def resolve(token: JsonWebToken): Future[AuthenticationResult[Auth]] = fromBearerToken(token).map {
      case Some(user) => grant(user)
      case None => deny(None)
    }

    val authenticator: Option[String] => Future[AuthenticationResult[Auth]] = {
      case Some(tokenStr) =>
        JsonWebToken.read(tokenStr, bearerTokenSecret) match {
          case Right(token) => resolve(token)
          case Left(Expired(token)) if acceptExpired => resolve(token)
          case Left(error) => Future(deny(Some(error)))
        }
      case None => Future(deny(Some(Missing)))
    }

    extractExecutionContext.flatMap { implicit ec =>
      optionalCookie("access_token").flatMap { (cookieTokenOption: Option[HttpCookiePair]) =>
        extractCredentials.flatMap { (credOption: Option[HttpCredentials]) => {

          val cookieToken = cookieTokenOption.map(_.value)
          val credToken = credOption.map(_.token())
          val tokenOption = credToken.orElse(cookieToken)

            onSuccess(authenticator(tokenOption)).flatMap {
              case Right(user) => setCookie(HttpCookie.fromPair(
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
/*
  def bearerToken(acceptExpired: Boolean = false): AuthenticationDirective[Auth] = {
    def resolve(token: JsonWebToken): Future[AuthenticationResult[Auth]] = fromBearerToken(token).map {
      case Some(user) => grant(user)
      case None => deny(None)
    }

    authenticateOrRejectWithChallenge[OAuth2BearerToken, Auth] {
      case Some(OAuth2BearerToken(tokenStr)) =>
        JsonWebToken.read(tokenStr, bearerTokenSecret) match {
          case Right(token) => resolve(token)
          case Left(Expired(token)) if acceptExpired => resolve(token)
          case Left(error) => Future(deny(Some(error)))
        }
      case None => Future(deny(Some(Missing)))
    }
  }
*/
  private def grant(user: Auth) = AuthenticationResult.success(user)
  private def deny = AuthenticationResult.failWithChallenge(createBasicChallenge)
  private def deny(error: Option[Error]) = AuthenticationResult.failWithChallenge(createBearerTokenChallenge(error))

  private def createBasicChallenge: HttpChallenge = {
    HttpChallenge("Basic", realm)
  }

  private def createBearerTokenChallenge(error: Option[Error]): HttpChallenge = {
    val desc = error match {
      case None => None
      case Some(Missing) => None
      case Some(Malformed) => Some("The access token is malformed")
      case Some(InvalidSignature) => Some("The access token has been manipulated")
      case Some(Incomplete) => Some("The token must at least contain the iat and exp claim")
      case Some(Expired(_)) => Some("The access token expired")
      case Some(UnsupportedAlgorithm(algo)) => Some(s"The signature algorithm $algo is not supported")
      case Some(Unknown) => Some("An unknown error occured")
    }
    val params = desc match {
      case Some(msg) => Map("error" -> "invalid_token", "error_description" -> msg)
      case None => Map.empty[String, String]
    }
    HttpChallenge("Bearer", realm, params)
  }
}
