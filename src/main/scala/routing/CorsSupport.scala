package routing

import akka.http.scaladsl.model.{HttpHeader, HttpMethod, HttpResponse}
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Credentials`
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Methods`
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Origin`
import akka.http.scaladsl.model.headers.Origin
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._

trait CorsSupport {

  protected def corsAllowOrigins: List[String]

  protected def corsAllowedHeaders: List[String]

  protected def corsAllowCredentials: Boolean

  protected def corsAllowedMethods: List[HttpMethod]

  protected def optionsCorsHeaders: List[HttpHeader]

  protected def corsRejectionHandler(allowOrigin: `Access-Control-Allow-Origin`) = RejectionHandler
    .newBuilder().handle {
    case MethodRejection(supported) =>
      complete(HttpResponse().withHeaders(
        `Access-Control-Allow-Methods`(OPTIONS, supported) ::
          allowOrigin ::
          optionsCorsHeaders
      ))
    case AuthenticationFailedRejection(cause, challenge) =>
      complete(HttpResponse().withHeaders(
          allowOrigin ::
          optionsCorsHeaders
      ))
  }
    .result()

  private def originToAllowOrigin(origin: Origin): Option[`Access-Control-Allow-Origin`] =
    if (corsAllowOrigins.contains("*") || corsAllowOrigins.contains(origin.value))
      origin.origins.headOption.map(`Access-Control-Allow-Origin`.apply)
    else
      None

  def cors[T]: Directive0 = mapInnerRoute { route => context =>
    ((context.request.method, context.request.header[Origin].flatMap(originToAllowOrigin)) match {
      case (OPTIONS, Some(allowOrigin)) =>
        handleRejections(corsRejectionHandler(allowOrigin)) {
          respondWithHeaders(allowOrigin :: optionsCorsHeaders) {
            route
          }
        }
      case (_, Some(allowOrigin)) =>
        respondWithHeaders(allowOrigin :: optionsCorsHeaders) {
          route
        }
      case (_, _) =>
        route
    })(context)
  }
}
