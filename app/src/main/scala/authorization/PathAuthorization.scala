package authorization

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives._
import akka.http.scaladsl.server.directives.SecurityDirectives._
import akka.http.scaladsl.server.directives.ParameterDirectives._
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import scala.util.{Failure, Success, Try}

class PathAuthorization(bearerTokenSecret: Array[Byte]) {

  def authorizePath: Directive0 = {
    extractRequestContext.flatMap { ctx =>
      parameter('access_token).flatMap { accessToken =>
        println(s"Checking if uri is correct: ${ctx.request.uri.path}")

        val algorithmHS = Algorithm.HMAC256(bearerTokenSecret)
        val verifier = JWT
          .require(algorithmHS)
          .build()
        val jwtTry = Try(verifier.verify(accessToken))

        jwtTry match {
          case Success(token) => authorize(token.getClaim("sub").asString == ctx.request.uri.path.toString) // FIXME can be null
          case Failure(_)     => authorize(false)
        }
      }
    }
  }
}

object PathAuthorization {
  type PathAuthorizationDirective = Directive0
}
