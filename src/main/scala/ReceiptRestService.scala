import java.io.File

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.Logger
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{HttpHeader, HttpMethod, HttpMethods}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.{
  `Access-Control-Allow-Credentials`,
  `Access-Control-Allow-Headers`,
  `Access-Control-Allow-Methods`,
  `Access-Control-Max-Age`
}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.{ActorMaterializer, Materializer}
import authentication.JwtAuthenticator
import authorization.PathAuthorization
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import model._
import repository.{OcrRepository, PendingFileRepository, ReceiptRepository, UserRepository}
import routing._
import service._

import scala.concurrent.ExecutionContextExecutor
import interpreters._
import ocr.service.{GoogleOcrService, OcrServiceStub}
import processing.ReceiptFiles
import queue.{Queue, QueueProcessor}
import queue.files.ReceiptFileQueue
import cats.implicits._
import freek._
// http://bandrzejczak.com/blog/2015/12/06/sso-for-your-single-page-application-part-2-slash-2-akka-http/

trait Service extends JsonProtocols with CorsSupport {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  override val corsAllowOrigins: List[String] = List("*")
  override val corsAllowedHeaders: List[String] = List("Origin",
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
  override val corsAllowedMethods =
    List[HttpMethod](HttpMethods.OPTIONS, HttpMethods.PATCH, HttpMethods.POST, HttpMethods.PUT, HttpMethods.GET, HttpMethods.DELETE)
  override val optionsCorsHeaders: List[HttpHeader] = List[HttpHeader](
    `Access-Control-Allow-Headers`(corsAllowedHeaders.mkString(", ")),
    `Access-Control-Max-Age`(60 * 60 * 24 * 20), // cache pre-flight response for 20 days
    `Access-Control-Allow-Credentials`(corsAllowCredentials),
    `Access-Control-Allow-Methods`(corsAllowedMethods)
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
    RejectionHandler
      .newBuilder()
      .handle {
        case AuthorizationFailedRejection =>
          complete((Forbidden -> ErrorResponse("Access forbidden")))
      }
      .handle {
        case MissingFormFieldRejection(field) =>
          complete((BadRequest -> ErrorResponse(s"Request is missing required form field '${field}'")))
      }
      .handle {
        case AuthenticationFailedRejection(cause, challenge) =>
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

  override implicit val system       = ActorSystem()
  override implicit val executor     = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()

  val userRepository     = new UserRepository()
  val googleOauthService = new GoogleOauthService()
//  val userService        = new UserService(userRepository, googleOauthService)

  val fileCachingService    = new FileCachingService()
  val imageResizingService  = new ImageResizingService()
  val pendingFileRepository = new PendingFileRepository()
  val pendingFileService    = new PendingFileService(pendingFileRepository)
  val fileService           = FileService.s3(config, system, materializer, fileCachingService, imageResizingService)

  val queue            = new Queue()
  val receiptFileQueue = new ReceiptFileQueue(queue)
  val receiptFiles     = new ReceiptFiles(pendingFileService, receiptFileQueue)

  val receiptRepository = new ReceiptRepository()
  val ocrRepository     = new OcrRepository()

  val receiptService = new ReceiptService(receiptRepository, ocrRepository)
  val ocrService =
    if (config.getBoolean("useOcrStub"))
      new OcrServiceStub()
    else
      new GoogleOcrService(new File(config.getString("googleApiCredentials")), imageResizingService)

  val interpreters = Interpreters(
    userInterpreter = new UserInterpreter(userRepository, googleOauthService),
    tokenInterpreter = new TokenInterpreter(),
    randomInterpreter = new RandomInterpreter(),
    fileInterpreter = new FileInterpreter(new PendingFileRepository(), receiptFileQueue, fileService),
    receiptInterpreter = new ReceiptInterpreter(receiptRepository, ocrRepository),
    ocrInterpreter = new OcrInterpreter(ocrRepository, ocrService),
    pendingFileInterpreter = new PendingFileInterpreter(pendingFileRepository)
  )

  val authenticatorInterpreters = interpreters.userInterpreter :&: interpreters.randomInterpreter
  val authenticator = new JwtAuthenticator[User](
    realm = "Example realm",
    bearerTokenSecret = config.getString("tokenSecret").getBytes,
    fromBearerToken = token => UserService.findById(token.claimAsString("sub").right.get).interpret(authenticatorInterpreters),
    fromUsernamePassword = (userName: String, password: String) =>
      UserService.findByUserNameWithPassword(userName, password).interpret(authenticatorInterpreters)
  )

  val pathAuthorization = new PathAuthorization(bearerTokenSecret = config.getString("tokenSecret").getBytes)

  logger.info("Testing logging")
  println("Mongo:")
  println(config.getString("mongodb.db"))

  //override val logger = Logging(system, getClass)
  override val receiptRouting =
    new ReceiptRouting(interpreters, authenticator.bearerTokenOrCookie(acceptExpired = true))
  override val pendingFileRouting = new PendingFileRouting(
    pendingFileService,
    authenticator.bearerTokenOrCookie(acceptExpired = true)
  )
  override val userRouting           = new UserRouting(interpreters, authenticator.bearerTokenOrCookie(acceptExpired = true))
  override val authenticationRouting = new AuthenticationRouting(authenticator)
  override val appConfigRouting      = new AppConfigRouting()
  override val oauthRouting          = new OauthRouting(interpreters)

  val backupService = new BackupService(receiptService, fileService)

  override val backupRouting =
    new BackupRouting(authenticator.bearerTokenOrCookie(acceptExpired = true), pathAuthorization.authorizePath, backupService)

  val queueProcessor = new QueueProcessor(
    queue = queue,
    interpreters = interpreters,
    system = system
  )

  queueProcessor.reserveNextJob()

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
