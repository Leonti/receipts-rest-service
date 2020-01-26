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

import scala.concurrent.duration._
import config.AppConfig

object ReceiptRestService extends IOApp {

  def configToAlgebras(config: AppConfig): (RoutingAlgebras[IO], AmazonS3) = {

    val httpExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(30))
    val (httpClient, _) = BlazeClientBuilder[IO](httpExecutor)
      .withResponseHeaderTimeout(60.seconds)
      .withRequestTimeout(60.seconds)
      .resource
      .allocated
      .unsafeRunSync()

    val openIdService = new OpenIdService(httpClient)
    val dynamoDbClient: AmazonDynamoDBAsync = {
      val credentials = new BasicAWSCredentials(config.awsConfig.accessKey, config.awsConfig.secretKey)

      AmazonDynamoDBAsyncClient
        .asyncBuilder()
        .withCredentials(new AWSStaticCredentialsProvider(credentials))
        .withRegion(config.awsConfig.region)
        .build()
    }
    val userInterpreter    = new UserDynamo(openIdService, dynamoDbClient, s"user-ids-${config.env}")
    val randomInterpreter  = new RandomInterpreterTagless()
    val receiptInterpreter = new ReceiptsStoreDynamo(dynamoDbClient, s"receipts-${config.env}")

    val fileExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(30))
    val localFile    = new LocalFileInterpreter(fileExecutor)

    val amazonS3Client: AmazonS3 = {
      val credentials = new BasicAWSCredentials(config.awsConfig.accessKey, config.awsConfig.secretKey)

      val amazonS3ClientBuilder = AmazonS3ClientBuilder
        .standard()
        .withCredentials(new AWSStaticCredentialsProvider(credentials))
      amazonS3ClientBuilder.withRegion(config.awsConfig.region).build()
    }
    val remoteFile = new RemoteFileS3(
      config.awsConfig,
      amazonS3Client,
      fileExecutor
    )

    val fileStore   = new FileStoreDynamo(dynamoDbClient, s"files-${config.env}")
    val pendingFile = new PendingFileStoreDynamo(dynamoDbClient, s"pending-files-${config.env}")

    val amazonSqsClient: AmazonSQS = {
      val credentials = new BasicAWSCredentials(config.awsConfig.accessKey, config.awsConfig.secretKey)

      val amazonS3ClientBuilder = AmazonSQSClientBuilder
        .standard()
        .withCredentials(new AWSStaticCredentialsProvider(credentials))
      amazonS3ClientBuilder.withRegion(config.awsConfig.region).build()
    }
    val queue = new QueueSqs(amazonSqsClient, s"receipt-jobs-${config.env}")

    val receiptSearch = new ReceiptSearch(httpClient, config.searchConfig)

    (
      RoutingAlgebras[IO](
        jwtVerificationAlg = new JwtVerificationInterpreter(),
        userAlg = userInterpreter,
        randomAlg = randomInterpreter,
        receiptStoreAlg = receiptInterpreter,
        localFileAlg = localFile,
        remoteFileAlg = remoteFile,
        fileStoreAlg = fileStore,
        pendingFileAlg = pendingFile,
        queueAlg = queue,
        receiptSearchAlg = receiptSearch
      ),
      amazonS3Client
    )
  }

  override def run(args: List[String]): IO[ExitCode] = {

    val config               = AppConfig.readConfig.unsafeRunSync
    val (algebras, s3Client) = ReceiptRestService.configToAlgebras(config)

    val imageResizer = new ImageInt()
    val ocrService =
      if (config.useOcrStub) {
        println("Using OCR stub")
        new OcrServiceStub()
      } else
        new GoogleOcrService(config.googleApiCredentials, imageResizer)

    val ocrInterpreter =
      new OcrInterpreterTagless(
        config.awsConfig,
        s3Client,
        ocrService
      )

    val fileProcessor = new FileProcessor(
      algebras.receiptStoreAlg,
      algebras.pendingFileAlg,
      algebras.localFileAlg,
      algebras.remoteFileAlg,
      imageResizer,
      algebras.randomAlg
    )
    val ocrProcessor = new OcrProcessor[IO](
      algebras.remoteFileAlg,
      algebras.localFileAlg,
      ocrInterpreter,
      algebras.randomAlg,
      algebras.pendingFileAlg,
      algebras.receiptSearchAlg
    )

    val queueExecutor  = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(30))
    val queueProcessor = new QueueProcessor(algebras.queueAlg, fileProcessor, ocrProcessor, queueExecutor)

    queueProcessor.reserveNextJob().unsafeRunAsync {
      case Right(_) => println("Queue processor finished running")
      case Left(e)  => println(s"Queue processor stopped with an error $e")
    }

    val backupExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(30))
    val routing        = new Routing[IO](algebras, config.routingConfig, backupExecutor)

    val httpApp = Router("/" -> routing.routes).orNotFound

    val serve = BlazeServerBuilder[IO]
      .bindHttp(9000, "0.0.0.0")
      .withHttpApp(httpApp)
      .serve

    serve.compile.drain.as(ExitCode.Success)
  }
}
