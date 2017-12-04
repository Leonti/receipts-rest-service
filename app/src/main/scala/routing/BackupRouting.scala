package routing

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthorizationFailedRejection, RejectionHandler}
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.stream.ActorMaterializer
import model.{ErrorResponse, JsonProtocols, User}
import service.{BackupService, JwtTokenGenerator}
import authorization.PathAuthorization.PathAuthorizationDirective
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ContentDispositionTypes, `Content-Disposition`}
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContextExecutor

class BackupRouting(authenticaton: AuthenticationDirective[User], authorizePath: PathAuthorizationDirective, backupService: BackupService)(
    implicit system: ActorSystem,
    executor: ExecutionContextExecutor,
    materializer: ActorMaterializer)
    extends JsonProtocols {

  private val config                         = ConfigFactory.load()
  private val bearerTokenSecret: Array[Byte] = config.getString("tokenSecret").getBytes

  def myRejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case AuthorizationFailedRejection =>
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
                complete(
                  Created -> JwtTokenGenerator.generatePathToken(s"/user/$userId/backup/download",
                                                                 System.currentTimeMillis(),
                                                                 bearerTokenSecret))
              }
            }
          }
        } ~
        path("download") {
          authorizePath {
            get {

              val backup = backupService.createUserBackup(userId)

              val contentDisposition = `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> backup.filename))

              complete(
                HttpResponse(headers = List(contentDisposition),
                             entity = HttpEntity(contentType = ContentType(MediaTypes.`application/zip`, () => HttpCharsets.`UTF-8`),
                                                 data = backup.source)))

            }

          }
        }
      }
    }
}
