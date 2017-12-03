package routing

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import model._
import org.slf4j.LoggerFactory
import service.PendingFileService

import scala.concurrent.ExecutionContextExecutor
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

class PendingFileRouting(
    pendingFileService: PendingFileService,
    authenticaton: AuthenticationDirective[User]
)(implicit system: ActorSystem, executor: ExecutionContextExecutor, materializer: ActorMaterializer)
    extends JsonProtocols {

  val logger = Logger(LoggerFactory.getLogger("PendingFileRouting"))

  val routes =
    pathPrefix("user" / Segment / "pending-file") { userId: String =>
      authenticaton { user =>
        authorize(user.id == userId) {
          get {
            val pendingFilesFuture = pendingFileService.findForUserId(userId)
            onComplete(pendingFilesFuture) { pendingFilesTry =>
              complete(pendingFilesTry)
            }
          }
        }
      }
    }

}
