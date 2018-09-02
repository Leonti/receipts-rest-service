import java.io.File
import java.util.concurrent.Executors
import authentication.BearerAuth
import cats.effect.IO
import io.finch.circe._
import com.twitter.finagle.Http
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.http.filter.Cors
import com.twitter.util.Await
import model._
import repository._
import routing.ExceptionEncoders._
import routing._
import service._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import interpreters._
import ocr.service.{GoogleOcrService, OcrServiceStub}
import processing.{FileProcessorTagless, OcrProcessorTagless, ReceiptFiles}
import queue.{Queue, QueueProcessor}
import queue.files.ReceiptFileQueue
import instances.catsio._
import io.finch.Endpoint
import org.http4s.client.Client
import org.http4s.client.blaze.{BlazeClientConfig, Http1Client}

import scala.concurrent.duration._

object ReceiptRestService extends App {
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

  implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  val userRepository = new UserRepository()
  val httpClient: Client[IO] = Http1Client[IO](
    BlazeClientConfig.defaultConfig.copy(
      responseHeaderTimeout = 60.seconds,
      requestTimeout = 60.second
    )).unsafeRunSync
  val openIdService = new OpenIdService(httpClient)

  val imageResizingService  = new ImageResizingService()
  val pendingFileRepository = new PendingFileRepository()
  val pendingFileService    = new PendingFileService(pendingFileRepository)
  val fileService           = new S3FileService(imageResizingService)

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
    new FileInterpreterTagless(new StoredFileRepository(), new PendingFileRepository(), receiptFileQueue, fileService)
  val receiptInterpreter = new ReceiptInterpreterTagless(receiptRepository)
  val ocrInterpreter =
    new OcrInterpreterTagless(httpClient,
                              ocrRepository,
                              ocrService,
                              OcrIntepreter.OcrConfig(sys.env("OCR_SEARCH_HOST"), sys.env("OCR_SEARCH_API_KEY")))
  val pendingFileInterpreter = new PendingFileInterpreterTagless(pendingFileRepository)

  val userPrograms = new UserPrograms(userInterpreter, randomInterpreter)

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
  val backupEndpoints    = new BackupEndpoints[IO](auth, new BackupService[IO](receiptInterpreter, fileInterpreter), tokenInterpreter)

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

  Await.ready(Http.server.serve(s"0.0.0.0:9000", service))
}
