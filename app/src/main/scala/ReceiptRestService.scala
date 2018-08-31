import java.io.File

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.Logger
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import authentication.BearerAuth
import cats.effect.IO
import io.finch.circe._
import com.twitter.finagle.Http
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.http.filter.Cors
import com.twitter.util.Await
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import model._
import repository._
import routing.ExceptionEncoders._
import routing._
import service._

import scala.concurrent.ExecutionContextExecutor
import interpreters._
import ocr.service.{GoogleOcrService, OcrServiceStub}
import processing.{FileProcessorTagless, OcrProcessorTagless, ReceiptFiles}
import queue.{Queue, QueueProcessor}
import queue.files.ReceiptFileQueue
import instances.catsio._
import io.finch.Endpoint
// http://bandrzejczak.com/blog/2015/12/06/sso-for-your-single-page-application-part-2-slash-2-akka-http/

trait AkkaHttpService {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  val policy: Cors.Policy = Cors.Policy(
    allowsOrigin = _ => Some("*"),
    allowsMethods = _ => Some(Seq("OPTIONS", "PATCH", "POST", "PUT", "GET", "DELETE")),
    allowsHeaders = _ =>
      Some(
        Seq("Origin",
            "X-Requested-With",
            "Content-Type",
            "Accept",
            "Accept-Encoding",
            "Accept-Language",
            "Host",
            "Referer",
            "User-Agent",
            "Authorization"))
  )

  def config: Config
}

object ReceiptRestService extends App with AkkaHttpService {
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
  val fileService           = FileService.s3(materializer, fileCachingService, imageResizingService)

  val queue            = new Queue()
  val receiptFileQueue = new ReceiptFileQueue(queue)
  val receiptFiles     = new ReceiptFiles(pendingFileService, receiptFileQueue)

  val receiptRepository = new ReceiptRepository()
  val ocrRepository     = new OcrRepository()

  val ocrService =
    if (sys.env("USE_OCR_STUB").toBoolean) {
      println("Using OCR stub")
      new OcrServiceStub()
    } else
      new GoogleOcrService(new File(sys.env("GOOGLE_API_CREDENTIALS")), imageResizingService)

  val userInterpreter   = new UserInterpreter(userRepository, openIdService)
  val tokenInterpreter  = new TokenInterpreter[IO](sys.env("AUTH_TOKEN_SECRET").getBytes)
  val randomInterpreter = new RandomInterpreterTagless()
  val fileInterpreter =
    new FileInterpreterTagless(new StoredFileRepository(), new PendingFileRepository(), receiptFileQueue, fileService)(materializer)
  val receiptInterpreter = new ReceiptInterpreterTagless(receiptRepository)
  val ocrInterpreter =
    new OcrInterpreterTagless(ocrRepository,
                              ocrService,
                              OcrIntepreter.OcrConfig(sys.env("OCR_SEARCH_HOST"), sys.env("OCR_SEARCH_API_KEY")))
  val pendingFileInterpreter = new PendingFileInterpreterTagless(pendingFileRepository)

  val userPrograms = new UserPrograms(userInterpreter, randomInterpreter)

  logger.info("Testing logging")

  val receiptPrograms = new ReceiptPrograms[IO](
    receiptInterpreter,
    fileInterpreter,
    randomInterpreter,
    ocrInterpreter
  )
  val fileUploadPrograms = new FileUploadPrograms[IO](
    sys.env("UPLOADS_FOLDER"),
    fileInterpreter,
    randomInterpreter
  )

  val auth: Endpoint[User] = new BearerAuth[IO, User](
    new JwtVerificationInterpreter(),
    subClaim => userPrograms.findUserByExternalId(subClaim.value)
  ).auth

  val receiptEndpoints =
    new ReceiptEndpoints[IO](auth, receiptPrograms, fileUploadPrograms)

  val pendingFileEndpoints = new PendingFileEndpoints[IO](auth, pendingFileInterpreter)

  val userEndpoints      = new UserEndpoints(auth)
  val appConfigEndpoints = new AppConfigEndpoints(sys.env("GOOGLE_CLIENT_ID"))
  val oauthEndpoints     = new OauthEndpoints[IO](userPrograms)

  val backupService   = new BackupService(receiptInterpreter, fileInterpreter)
  val backupEndpoints = new BackupEndpoints[IO](auth, new BackupServiceIO[IO](receiptInterpreter, fileInterpreter), tokenInterpreter)

  val fileProcessor  = new FileProcessorTagless(receiptInterpreter, fileInterpreter)
  val ocrProcessor   = new OcrProcessorTagless[IO](fileInterpreter, ocrInterpreter, randomInterpreter, pendingFileInterpreter)
  val queueProcessor = new QueueProcessor(queue, fileProcessor, ocrProcessor)

  queueProcessor.reserveNextJob().unsafeRunAsync {
    case Right(_) => println("Queue processor finished running")
    case Left(e)  => println(s"Queue processor stopped with an error $e")
  }

  val service: Service[Request, Response] = new Cors.HttpFilter(policy).andThen(
    (userEndpoints.userInfo :+:
      receiptEndpoints.all :+:
      pendingFileEndpoints.pendingFiles :+:
      appConfigEndpoints.getAppConfig :+:
      oauthEndpoints.validateWithUserCreation :+:
      backupEndpoints.all :+:
      new VersionEndpoint(System.getenv("VERSION")).version).toService)

  val port = config.getInt("http.port")
  Await.ready(Http.server.serve(s"0.0.0.0:$port", service))
}
