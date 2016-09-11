import java.util.Date

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.Logger
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Credentials`, `Access-Control-Allow-Headers`, `Access-Control-Max-Age`}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl._
import akka.util.ByteString
import authorization.PathAuthorization
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import model.ReceiptEntity
import model._
import repository.{PendingFileRepository, ReceiptRepository, UserRepository}
import routing._
import service._

import scala.collection.mutable
import scala.concurrent.{ExecutionContextExecutor, Future}
import spray.json._
import de.choffmeister.auth.akkahttp.Authenticator
import de.choffmeister.auth.common._
import processing.{FileProcessor, ReceiptFiles}
import queue.{Queue, QueueProcessor}
import queue.files.ReceiptFileQueue

import scala.concurrent.duration._
import scala.util.Try
import scala.util.Success
import scala.util.Failure

// http://bandrzejczak.com/blog/2015/12/06/sso-for-your-single-page-application-part-2-slash-2-akka-http/

trait Service extends JsonProtocols with CorsSupport {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  override val corsAllowOrigins: List[String] = List("*")
  override val corsAllowedHeaders: List[String] = List(
    "Origin",
    "X-Requested-With",
    "Content-Type",
    "Accept",
    "Accept-Encoding",
    "Accept-Language",
    "Host",
    "Referer",
    "User-Agent",
    "Authorization")
  override val corsAllowCredentials: Boolean = true
  override val optionsCorsHeaders: List[HttpHeader] = List[HttpHeader](
    `Access-Control-Allow-Headers`(corsAllowedHeaders.mkString(", ")),
    `Access-Control-Max-Age`(60 * 60 * 24 * 20), // cache pre-flight response for 20 days
    `Access-Control-Allow-Credentials`(corsAllowCredentials)
  )

  def config: Config
  //val logger: LoggingAdapter

  val userRouting: UserRouting
  val receiptRouting: ReceiptRouting
  val pendingFileRouting: PendingFileRouting
  val authenticationRouting: AuthenticationRouting
  val appConfigRouting: AppConfigRouting
  val oauthRouting: OauthRouting
  val backupRouting: BackupRouting

  def myRejectionHandler =
    RejectionHandler.newBuilder()
      .handle { case AuthorizationFailedRejection =>
        complete((Forbidden -> ErrorResponse("Access forbidden")))
      }
      .handle { case MissingFormFieldRejection(field) =>
        complete((BadRequest -> ErrorResponse(s"Request is missing required form field '${field}'")))
      }
        .handle { case AuthenticationFailedRejection(cause, challenge) =>
          complete((Unauthorized -> ErrorResponse("The supplied authentication is invalid")))
        }
      .result()

  val routes = {
    //logRequest("receipt-rest-service") {
     // logRequestResult("receipt-rest-service") {
        handleRejections(myRejectionHandler) {
          cors {
            userRouting.routes ~
            receiptRouting.routes ~
            pendingFileRouting.routes ~
            authenticationRouting.routes ~
            appConfigRouting.routes ~
            oauthRouting.routes ~
            backupRouting.routes ~
            path("version") {
              get {
                complete(Created -> System.getenv("VERSION"))
              }
            }
          }
        }
     // }
   // }

  }
}

object ReceiptRestService extends App with Service {
  val logger = Logger(LoggerFactory.getLogger("ReceiptRestService"))

  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  val userService = new UserService(new UserRepository())
  val googleOauthService = new GoogleOauthService()
  override val config = ConfigFactory.load()

  val authenticator = new Authenticator[User](
    realm = "Example realm",
    bearerTokenSecret = config.getString("tokenSecret").getBytes,
    fromBearerToken = token => userService.findById(token.claimAsString("sub").right.get),
    fromUsernamePassword = userService.findByUserNameWithPassword
  )

  val pathAuthorization = new PathAuthorization(bearerTokenSecret = config.getString("tokenSecret").getBytes)

  logger.info("Testing logging")
  println("Mongo:")
  println(config.getString("mongodb.db"))

  val fileCachingService = new FileCachingService()
  val imageResizingService = new ImageResizingService()
  val receiptService = new ReceiptService(new ReceiptRepository())
  val pendingFileService = new PendingFileService(new PendingFileRepository())
  val fileService = FileService.s3(config, materializer, fileCachingService, imageResizingService)

  val queue = new Queue()
  val receiptFiles = new ReceiptFiles(pendingFileService, new ReceiptFileQueue(queue))

  //override val logger = Logging(system, getClass)
  override val receiptRouting = new ReceiptRouting(
    receiptService,
    fileService,
    receiptFiles,
    authenticator.bearerToken(acceptExpired = true))
  override val pendingFileRouting = new PendingFileRouting(
    pendingFileService,
    authenticator.bearerToken(acceptExpired = true)
  )
  override val userRouting = new UserRouting(userService, authenticator.bearerToken(acceptExpired = true))
  override val authenticationRouting = new AuthenticationRouting(authenticator)
  override val appConfigRouting = new AppConfigRouting()
  override val oauthRouting = new OauthRouting(userService, googleOauthService)

  val backupService = new BackupService(receiptService, fileService)

  override val backupRouting = new BackupRouting(
    authenticator.bearerToken(acceptExpired = true),
    pathAuthorization.authorizePath,
    backupService)

  val fileProcessor = new FileProcessor(
    fileService = fileService,
    receiptService = receiptService,
    pendingFileService = pendingFileService
  )

  val queueProcessor = new QueueProcessor(
    queue = queue,
    fileProcessor = fileProcessor,
    system = system
  )

  queueProcessor.reserveNextJob()

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
