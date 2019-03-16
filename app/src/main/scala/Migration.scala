import java.io.File
import java.util.concurrent.Executors

import cats.effect.IO
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClient}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import interpreters._
import repository.{ReceiptRepository, StoredFileRepository}
import user.UserId
import cats.implicits._
import receipt.{RemoteFileId, StoredFile}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
object Migration extends App {

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

  val oldReceipts = new ReceiptStoreMongo(new ReceiptRepository())
  val receiptStore = new ReceiptsStoreDynamo(dynamoDbClient, s"receipts-prod")

  val userId = UserId("fbd953fe-3da2-46d3-9a8d-49e37ddf7e88")

  val migrateReceipts = oldReceipts.userReceipts(userId).flatMap(_.toList
    .traverse(receipt => {
      receiptStore.saveReceipt(receipt).flatMap(receipt => IO(println(s"Migrated receipt ${receipt.id}")))
    }))
  migrateReceipts.unsafeRunSync()

  val oldFiles = new FileStoreMongo(new StoredFileRepository())
  val fileStore = new FileStoreDynamo(dynamoDbClient, s"files-prod")

  private implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  val remoteFile = new RemoteFileS3(awsConfig, amazonS3Client, executor)
  val localFile = new LocalFileInterpreter(executor)
  val randomInterpreter = new RandomInterpreterTagless()

  val migrateFiles = oldReceipts.userReceipts(userId).flatMap(_.toList
    .traverse(receipt => {
      receipt.files.find(_.parentId.isEmpty) match {
        case Some(fileEntity) => for {
          fileStream <- remoteFile.remoteFileStream(RemoteFileId(UserId(receipt.userId), fileEntity.id))
          tmpFile = new File(new File("/tmp"), fileEntity.id)
          _ <- localFile.streamToFile(fileStream, tmpFile)
          md5 <- localFile.getMd5(tmpFile)
          _ <- localFile.removeFile(tmpFile)
          _ <- fileStore.saveStoredFile(StoredFile(
            userId = receipt.userId,
            id = fileEntity.id,
            md5 = md5
          ))
          _ <- IO(println(s"StoredFile is migrated ${fileEntity.id}"))
        } yield ()
        case None => IO.pure(())
      }
    }))

 // migrateFiles.unsafeRunSync()

/*
  receiptStore.saveReceipt(ReceiptEntity(
    "id-2",
    "userId",
    List(),
    "",
    None,
    1,
    1,
    1,
    List()
  )).unsafeRunSync()
*/
  println("receipt saved")
}
