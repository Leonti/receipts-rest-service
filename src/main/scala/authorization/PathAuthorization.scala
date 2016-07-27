package authorization

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives._
import akka.http.scaladsl.server.directives.SecurityDirectives._
import akka.http.scaladsl.server.directives.ParameterDirectives._
import de.choffmeister.auth.common.JsonWebToken

class PathAuthorization(bearerTokenSecret: Array[Byte]) {

  def authorizePath: Directive0 = {
    extractRequestContext.flatMap { ctx =>
      parameter('access_token).flatMap { accessToken =>
        println(s"Checking if uri is correct: ${ctx.request.uri.path}")

        JsonWebToken.read(accessToken, bearerTokenSecret) match {
          case Right(token) => authorize(token.claimAsString("sub").right.get == ctx.request.uri.path.toString())
          case Left(_) => authorize(false)
        }
      }
    }
  }
}

object PathAuthorization {
  type PathAuthorizationDirective = Directive0
}
