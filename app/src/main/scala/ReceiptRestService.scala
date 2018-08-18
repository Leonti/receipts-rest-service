import java.io.File

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.Logger
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
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
import repository._
import routing._
import service._

import scala.concurrent.{ExecutionContextExecutor, Future}
import interpreters._
import ocr.service.{GoogleOcrService, OcrServiceStub}
import processing.{FileProcessorTagless, OcrProcessorTagless, ReceiptFiles}
import queue.{Queue, QueueProcessor}
import queue.files.ReceiptFileQueue
import cats.implicits._
// http://bandrzejczak.com/blog/2015/12/06/sso-for-your-single-page-application-part-2-slash-2-akka-http/

trait Service extends CorsSupport {
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

  def routes(config: Config) = {
    //logRequest("receipt-rest-service") {
    // logRequestResult("receipt-rest-service") {
    handleRejections(myRejectionHandler) {
      cors {
        userRouting.routes ~
        receiptRouting.routes(config.getString("uploadsFolder")) ~
        pendingFileRouting.routes ~
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

  val userRepository = new UserRepository()
  val openIdService  = new OpenIdService()

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

  val ocrService =
    if (config.getBoolean("useOcrStub")) {
      println("Using OCR stub")
      new OcrServiceStub()
    } else
      new GoogleOcrService(new File(config.getString("googleApiCredentials")), imageResizingService)

  val userInterpreter   = new UserInterpreter(userRepository, openIdService)
  val tokenInterpreter  = new TokenInterpreter()
  val randomInterpreter = new RandomInterpreterTagless()
  val fileInterpreter =
    new FileInterpreterTagless(new StoredFileRepository(), new PendingFileRepository(), receiptFileQueue, fileService)(materializer)
  val receiptInterpreter = new ReceiptInterpreterTagless(receiptRepository, ocrRepository)
  val ocrInterpreter =
    new OcrInterpreterTagless(ocrRepository,
                              ocrService,
                              OcrIntepreter.OcrConfig(sys.env("OCR_SEARCH_HOST"), sys.env("OCR_SEARCH_API_KEY")))
  val pendingFileInterpreter = new PendingFileInterpreterTagless(pendingFileRepository)

  val userPrograms = new UserPrograms(userInterpreter)

  val authenticator = new JwtAuthenticator[User](
    new JwtVerificationInterpreter(config.getString("tokenSecret").getBytes),
    realm = "Example realm",
    fromBearerTokenClaim = subClaim => userPrograms.findUserByExternalId(subClaim.value)
  )

  val pathAuthorization = new PathAuthorization(bearerTokenSecret = config.getString("tokenSecret").getBytes)

  logger.info("Testing logging")
  println("Mongo:")
  println(config.getString("mongodb.db"))

  val receiptPrograms = new ReceiptPrograms[Future](
    receiptInterpreter,
    fileInterpreter,
    randomInterpreter,
    ocrInterpreter
  )
  override val receiptRouting =
    new ReceiptRouting(receiptPrograms, authenticator.bearerTokenOrCookie(acceptExpired = true))
  override val pendingFileRouting = new PendingFileRouting(
    pendingFileService,
    authenticator.bearerTokenOrCookie(acceptExpired = true)
  )

  override val userRouting      = new UserRouting(userPrograms, authenticator.bearerTokenOrCookie(acceptExpired = true))
  override val appConfigRouting = new AppConfigRouting()
  override val oauthRouting     = new OauthRouting(userPrograms)

  val backupService = new BackupService(receiptPrograms, fileService)

  override val backupRouting =
    new BackupRouting(authenticator.bearerTokenOrCookie(acceptExpired = true), pathAuthorization.authorizePath, backupService)

  val fileProcessor  = new FileProcessorTagless(receiptInterpreter, fileInterpreter)
  val ocrProcessor   = new OcrProcessorTagless(fileInterpreter, ocrInterpreter, randomInterpreter, pendingFileInterpreter)
  val queueProcessor = new QueueProcessor(queue, fileProcessor, ocrProcessor, system)

  queueProcessor.reserveNextJob()

  Http().bindAndHandle(routes(config), config.getString("http.interface"), config.getInt("http.port"))
}
