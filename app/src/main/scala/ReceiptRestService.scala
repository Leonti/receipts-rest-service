import java.io.File
import java.util.concurrent.Executors

import cats.effect.{ContextShift, ExitCode, IO, IOApp}
import cats.implicits._
import repository._
import routing._
import service._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import interpreters.{ReceiptFileQueue, _}
import ocr.service.{GoogleOcrService, OcrServiceStub}
import processing.{FileProcessor, OcrProcessor}
import queue.{Queue, QueueProcessor}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.blaze._
import org.http4s.implicits._

import scala.concurrent.duration._

object ReceiptRestService extends IOApp {

  private implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private implicit val cs: ContextShift[IO] = IO.contextShift(executor)

  val userRepository = new UserRepository()

  // TODO: Properly use resource
  val (httpClient, _) = BlazeClientBuilder[IO](executor)
    .withResponseHeaderTimeout(60.seconds)
    .withRequestTimeout(60.seconds)
    .resource
    .allocated
    .unsafeRunSync()

  val pendingFileRepository = new PendingFileRepository()

  val queue            = new Queue()
  val receiptFileQueue = new ReceiptFileQueue(new Queue())

  val receiptRepository = new ReceiptRepository()
  val ocrRepository     = new OcrRepository()

  val imageResizer = new ImageMagickResizer()
  val ocrService =
    if (sys.env("USE_OCR_STUB").toBoolean) {
      println("Using OCR stub")
      new OcrServiceStub()
    } else
      new GoogleOcrService(new File(sys.env("GOOGLE_API_CREDENTIALS")), imageResizer)

  val openIdService      = new OpenIdService(httpClient)
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
    ),
    executor
  )
  val fileStore    = new FileStoreMongo(new StoredFileRepository())
  val pendingFile  = new PendingFileMongo(new PendingFileRepository())
  val receiptQueue = new ReceiptFileQueue(queue)

  val routingConfig = RoutingConfig(
    uploadsFolder = sys.env("UPLOADS_FOLDER"),
    googleClientId = sys.env("GOOGLE_CLIENT_ID")
  )

  val routing = new Routing[IO](
    RoutingAlgebras(
      jwtVerificationAlg = new JwtVerificationInterpreter(),
      userAlg = userInterpreter,
      randomAlg = randomInterpreter,
      receiptStoreAlg = receiptInterpreter,
      localFileAlg = localFile,
      remoteFileAlg = remoteFile,
      fileStoreAlg = fileStore,
      pendingFileAlg = pendingFile,
      queueAlg = receiptFileQueue,
      ocrAlg = ocrInterpreter,
      tokenAlg = tokenInterpreter
    ),
    routingConfig
  )

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

  override def run(args: List[String]): IO[ExitCode] = {

    val httpApp = Router("/" -> routing.routes).orNotFound

    val serve = BlazeServerBuilder[IO]
      .bindHttp(9000, "0.0.0.0")
      .withHttpApp(httpApp)
      .serve

    serve.compile.drain.as(ExitCode.Success)
  }
}
