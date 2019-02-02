import java.io.File
import java.util.concurrent.Executors

import authentication.BearerAuth
import backup.{BackupEndpoints}
import cats.effect.{ContextShift, IO}
import io.finch.circe._
import io.finch.fs2._
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
import interpreters.{ReceiptFileQueue, _}
import ocr.service.{GoogleOcrService, OcrServiceStub}
import processing.{FileProcessor, OcrProcessor}
import queue.{Queue, QueueProcessor}
import io.finch.Endpoint
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.duration._

// TODO switch to IOApp
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

  private implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private implicit val cs: ContextShift[IO] = IO.contextShift(executor)

  val userRepository = new UserRepository()

  // TODO: Properly use resource
  val (httpClient, _) = BlazeClientBuilder[IO](executor)
    .withResponseHeaderTimeout(60.seconds)
    .withRequestTimeout(60.seconds)
    .resource.allocated.unsafeRunSync()

  val openIdService = new OpenIdService(httpClient)

  val pendingFileRepository = new PendingFileRepository()

  val queue            = new Queue()
  val receiptFileQueue = new ReceiptFileQueue(queue)

  val receiptRepository = new ReceiptRepository()
  val ocrRepository     = new OcrRepository()

  val imageResizer = new ImageMagickResizer()
  val ocrService =
    if (sys.env("USE_OCR_STUB").toBoolean) {
      println("Using OCR stub")
      new OcrServiceStub()
    } else
      new GoogleOcrService(new File(sys.env("GOOGLE_API_CREDENTIALS")), imageResizer)

  val userInterpreter    = new UserInterpreter(userRepository, openIdService)
  val tokenInterpreter   = new TokenInterpreter[IO](sys.env("AUTH_TOKEN_SECRET").getBytes)
  val randomInterpreter  = new RandomInterpreterTagless()
  val receiptInterpreter = new ReceiptStoreMongo(new ReceiptRepository())
  val ocrInterpreter =
    new OcrInterpreterTagless(httpClient,
                              ocrRepository,
                              ocrService,
                              OcrIntepreter.OcrConfig(sys.env("OCR_SEARCH_HOST"), sys.env("OCR_SEARCH_API_KEY")))

  val userPrograms = new UserPrograms(userInterpreter, randomInterpreter)

  val localFile = new LocalFileInterpreter(executor)
  val remoteFile = new RemoteFileS3(
    S3Config(
      region = sys.env("S3_REGION"),
      bucket = sys.env("S3_BUCKET"),
      accessKey = sys.env("S3_ACCESS_KEY"),
      secretKey = sys.env("S3_SECRET_ACCESS_KEY")
    ), executor)
  val fileStore    = new FileStoreMongo(new StoredFileRepository())
  val pendingFile  = new PendingFileMongo(new PendingFileRepository())
  val receiptQueue = new ReceiptFileQueue(queue)

  val receiptPrograms = new ReceiptPrograms[IO](
    receiptInterpreter,
    localFile,
    remoteFile,
    fileStore,
    pendingFile,
    receiptQueue,
    randomInterpreter,
    ocrInterpreter
  )
  val fileUploadPrograms = new FileUploadPrograms[IO](
    sys.env("UPLOADS_FOLDER"),
    localFile,
    randomInterpreter
  )

  val auth: Endpoint[IO, User] = new BearerAuth[IO, User](
    new JwtVerificationInterpreter(),
    subClaim => userPrograms.findUserByExternalId(subClaim.value)
  ).auth

  val receiptEndpoints =
    new ReceiptEndpoints[IO](auth, receiptPrograms, fileUploadPrograms)

  val pendingFileEndpoints = new PendingFileEndpoints[IO](auth, pendingFile)

  val userEndpoints      = new UserEndpoints(auth)
  val appConfigEndpoints = new AppConfigEndpoints[IO](sys.env("GOOGLE_CLIENT_ID"))
  val oauthEndpoints     = new OauthEndpoints[IO](userPrograms)
  val backupEndpoints    = new BackupEndpoints[IO](auth, /*new BackupService[IO](receiptInterpreter, remoteFile),*/ tokenInterpreter)
  
  val fileProcessor = new FileProcessor(
    receiptInterpreter,
    localFile,
    remoteFile,
    imageResizer,
    randomInterpreter
  )
  val ocrProcessor   = new OcrProcessor[IO](remoteFile, localFile, ocrInterpreter, randomInterpreter, pendingFile)
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
      new VersionEndpoint[IO](System.getenv("VERSION")).version).toService)

  Await.ready(Http.server.serve(s"0.0.0.0:9000", service))
}
