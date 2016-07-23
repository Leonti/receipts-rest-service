package routing

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthorizationFailedRejection, MissingFormFieldRejection, RejectionHandler}
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.stream.ActorMaterializer
import model.{ErrorResponse, JsonProtocols, User}
import service.JwtTokenGenerator
import authorization.PathAuthorization.PathAuthorizationDirective

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.ExecutionContextExecutor

class BackupRouting(authenticaton: AuthenticationDirective[User],
                    authorizePath: PathAuthorizationDirective)
  (implicit system: ActorSystem, executor: ExecutionContextExecutor, materializer: ActorMaterializer) extends JsonProtocols{

  def myRejectionHandler =
    RejectionHandler.newBuilder()
      .handle { case AuthorizationFailedRejection =>
        complete(Forbidden -> ErrorResponse("Access forbidden"))
      }
      .result()

  val routes =
    handleRejections(myRejectionHandler) {
      pathPrefix("user" / Segment / "backup") { userId: String =>
        path("token") {
          authenticaton { user =>
            authorize(user.id == userId) {
              get {
                complete(Created -> JwtTokenGenerator.generatePathToken(s"/user/$userId/backup/download"))
              }
            }
          }
        } ~
        path("download") {
          authorizePath {
            complete(Created -> JwtTokenGenerator.generatePathToken(s"user/$userId/backup/download"))
          }
        }
      }
    }
}
