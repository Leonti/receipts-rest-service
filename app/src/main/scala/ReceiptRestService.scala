import java.io.File
import java.util.concurrent.Executors

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClient}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import routing._
import service._

import scala.concurrent.ExecutionContext
import interpreters._
import ocr.{GoogleOcrService, OcrServiceStub}
import processing.{FileProcessor, OcrProcessor}
import queue.{QueueProcessor, QueueSqs}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.blaze._
import org.http4s.implicits._
import user.UserPrograms

import scala.concurrent.duration._

object ReceiptRestService extends IOApp {

  val httpExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(30))
  val (httpClient, _) = BlazeClientBuilder[IO](httpExecutor)
    .withResponseHeaderTimeout(60.seconds)
    .withRequestTimeout(60.seconds)
    .resource
    .allocated
    .unsafeRunSync()

  val imageResizer = new ImageInt()
  val ocrService =
    if (sys.env("USE_OCR_STUB").toBoolean) {
      println("Using OCR stub")
      new OcrServiceStub()
    } else
      new GoogleOcrService(new File(sys.env("GOOGLE_API_CREDENTIALS")), imageResizer)

  val awsConfig = S3Config(
    region = sys.env("S3_REGION"),
    bucket = sys.env("S3_BUCKET"),
    accessKey = sys.env("S3_ACCESS_KEY"),
    secretKey = sys.env("S3_SECRET_ACCESS_KEY")
  )

  val amazonS3Client: AmazonS3 = {
    val credentials = new BasicAWSCredentials(awsConfig.accessKey, awsConfig.secretKey)

    val amazonS3ClientBuilder = AmazonS3ClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
    amazonS3ClientBuilder.withRegion(awsConfig.region).build()
  }

  val dynamoDbClient: AmazonDynamoDBAsync = {
    val credentials = new BasicAWSCredentials(awsConfig.accessKey, awsConfig.secretKey)

    AmazonDynamoDBAsyncClient
      .asyncBuilder()
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
      .withRegion(awsConfig.region)
      .build()
  }

  val env                = sys.env("ENV")
  val openIdService      = new OpenIdService(httpClient)
  val userInterpreter    = new UserDynamo(openIdService, dynamoDbClient, s"user-ids-$env")
  val randomInterpreter  = new RandomInterpreterTagless()
  val receiptInterpreter = new ReceiptsStoreDynamo(dynamoDbClient, s"receipts-$env")

  val ocrInterpreter =
    new OcrInterpreterTagless(httpClient,
                              awsConfig,
                              amazonS3Client,
                              ocrService,
                              OcrIntepreter.OcrConfig(sys.env("OCR_SEARCH_HOST"), sys.env("OCR_SEARCH_API_KEY")))

  val userPrograms = new UserPrograms(userInterpreter, randomInterpreter)

  val fileExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(30))
  val localFile    = new LocalFileInterpreter(fileExecutor)
  val remoteFile = new RemoteFileS3(
    awsConfig,
    amazonS3Client,
    fileExecutor
  )
  val fileStore   = new FileStoreDynamo(dynamoDbClient, s"files-$env")
  val pendingFile = new PendingFileStoreDynamo(dynamoDbClient, s"pending-files-$env")

  val routingConfig = RoutingConfig(
    uploadsFolder = sys.env("UPLOADS_FOLDER"),
    googleClientId = sys.env("GOOGLE_CLIENT_ID"),
    authTokenSecret = sys.env("AUTH_TOKEN_SECRET").getBytes
  )

  val amazonSqsClient: AmazonSQS = {
    val credentials = new BasicAWSCredentials(awsConfig.accessKey, awsConfig.secretKey)

    val amazonS3ClientBuilder = AmazonSQSClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
    amazonS3ClientBuilder.withRegion(awsConfig.region).build()
  }
  val queue = new QueueSqs(amazonSqsClient, s"receipt-jobs-$env")

  //  private implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  //  private implicit val cs: ContextShift[IO] = IO.contextShift(executor)

  val backupExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(30))
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
      queueAlg = queue,
      ocrAlg = ocrInterpreter
    ),
    routingConfig,
    backupExecutor
  )

  val fileProcessor = new FileProcessor(
    receiptInterpreter,
    pendingFile,
    localFile,
    remoteFile,
    imageResizer,
    randomInterpreter
  )
  val ocrProcessor = new OcrProcessor[IO](remoteFile, localFile, ocrInterpreter, randomInterpreter, pendingFile)

  val queueExecutor  = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(30))
  val queueProcessor = new QueueProcessor(queue, fileProcessor, ocrProcessor, queueExecutor)

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
