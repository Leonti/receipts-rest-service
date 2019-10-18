//import java.io.File
//import java.util.concurrent.Executors
//
//import cats.effect.IO
//import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
//import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClient}
//import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
//import fs2.Stream
//import interpreters._
//import cats.implicits._
////import model.WebSize
//import receipt.{ReceiptEntity, RemoteFileId}
////import receipt.RemoteFileId
//import user.UserId
////import scala.collection.JavaConverters._
//
//import scala.concurrent.ExecutionContext
//object Rotation extends App {
//
//  val awsConfig = S3Config(
//    region = sys.env("S3_REGION"),
//    bucket = sys.env("S3_BUCKET"),
//    accessKey = sys.env("S3_ACCESS_KEY"),
//    secretKey = sys.env("S3_SECRET_ACCESS_KEY")
//  )
//
//  val amazonS3Client: AmazonS3 = {
//    val credentials = new BasicAWSCredentials(awsConfig.accessKey, awsConfig.secretKey)
//
//    val amazonS3ClientBuilder = AmazonS3ClientBuilder
//      .standard()
//      .withCredentials(new AWSStaticCredentialsProvider(credentials))
//    amazonS3ClientBuilder.withRegion(awsConfig.region).build()
//  }
//
//  val dynamoDbClient: AmazonDynamoDBAsync = {
//    val credentials = new BasicAWSCredentials(awsConfig.accessKey, awsConfig.secretKey)
//
//    AmazonDynamoDBAsyncClient
//      .asyncBuilder()
//      .withCredentials(new AWSStaticCredentialsProvider(credentials))
//      .withRegion(awsConfig.region)
//      .build()
//  }
//
//  val env = "prod"
//
//  val receipts = new ReceiptsStoreDynamo(dynamoDbClient, s"receipts-$env")
//
//  val fileExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(30))
//  val localFile    = new LocalFileInterpreter(fileExecutor)
//  val remoteFile = new RemoteFileS3(
//    awsConfig,
//    amazonS3Client,
//    fileExecutor
//  )
//
//  val randomInt = new RandomInterpreterTagless()
//
//  val userId = UserId("fbd953fe-3da2-46d3-9a8d-49e37ddf7e88")
//
////  val recent = receipts.recentUserReceipts(userId, 1553126400000l).unsafeRunSync()
//
////  println(recent.head)
//
//  val mainFile = "02a24add-1bb9-4618-bd0d-ce36da27092d"
//  val resized  = "484c21c5-7c83-4123-b0c6-3d2d546b6b48"
//
////  val mainFileStream = remoteFile.remoteFileStream(RemoteFileId(userId, mainFile)).unsafeRunSync()
////  localFile.streamToFile(mainFileStream, new File("/home/leonti/Downloads/main_receipt.jpg")).unsafeRunSync()
//
////  val resizedFileStream = remoteFile.remoteFileStream(RemoteFileId(userId, resized)).unsafeRunSync()
////  localFile.streamToFile(resizedFileStream, new File("/home/leonti/Downloads/resized_receipt.jpg")).unsafeRunSync()
//
// // val metadata = ImageMetadataReader.readMetadata(new File("/home/leonti/Downloads/main_receipt.jpg"))
//
// // val directories = metadata.getDirectories.asScala.toList
//
//  //directories.foreach(println)
//  //println(metadata)
//
//  //amazonS3Client.copyObject()
//
//
//  val ocrInt = new OcrInterpreterTagless(
//    null,
//    awsConfig,
//    amazonS3Client,
//    null,
//    null
//  )
//
//  def processReceipt(receipt: ReceiptEntity): IO[Unit] = receipt.files match {
//    case fileEntities@List(_, _) => {
//      for {
//        _ <- IO(println(s"Processing ${receipt.id}"))
//        start = System.currentTimeMillis()
//        mainFile <- IO(fileEntities.find(_.parentId.isEmpty).get)
//        derived <- IO(fileEntities.find(_.parentId.isDefined).get)
//        originalFile <- randomInt.tmpFile()
//        fileStream <- remoteFileStream(RemoteFileId(UserId(receipt.userId), mainFile.id))
//        _ <- localFile.streamToFile(fileStream, originalFile)
//        originalMetadata <- imageInt.getImageMetaData(originalFile)
//        resizedFile <- imageInt.resizeToPixelSize(originalFile, WebSize.pixels)
//        resizedMetadata <- imageInt.getImageMetaData(resizedFile)
//        _ <- remoteFile.saveRemoteFile(originalFile, RemoteFileId(UserId(receipt.userId), receipt.id))
//        _ <- remoteFile.saveRemoteFile(resizedFile, RemoteFileId(UserId(receipt.userId), derived.id))
//        _ <- receipts.saveReceipt(receipt.copy(files = List()))
//        _ <- receipts.addFileToReceipt(UserId(receipt.userId), receipt.id, FileEntity(
//          id = receipt.id,
//          parentId = None,
//          ext = mainFile.ext,
//          metaData = originalMetadata,
//          timestamp = mainFile.timestamp
//        ))
//        _ <- receipts.addFileToReceipt(UserId(receipt.userId), receipt.id, FileEntity(
//          id = derived.id,
//          parentId = Some(receipt.id),
//          ext = mainFile.ext,
//          metaData = resizedMetadata,
//          timestamp = mainFile.timestamp
//        ))
//        _ <- deleteRemoteFile(RemoteFileId(UserId(receipt.userId), mainFile.id))
//        _ <- deleteRemoteFile(RemoteFileId(UserId(receipt.userId), derived.id))
//        end = System.currentTimeMillis()
//        _ <- IO(println(s"OK: ${receipt.id} ${end - start}ms"))
//      } yield ()
//    }
//    case other => IO {
//      println(s"NOT PROCESSING ${receipt.id}, because $other")
//    }
//  }
//
//
//  def processOcrReceipt(folder: File)(receipt: ReceiptEntity): IO[Unit] = (receipt.total, receipt.files) match {
//    case (Some(total), List(_, _)) => {
//      for {
//        _ <- IO(println(s"Processing ${receipt.id}"))
//        start = System.currentTimeMillis()
//        imagesFolder <- IO(new File(folder, "images"))
//        _ <- IO(imagesFolder.mkdirs())
//        originalFile <- IO(new File(imagesFolder, receipt.id))
//        fileStream <- remoteFile.remoteFileStream(RemoteFileId(UserId(receipt.userId), receipt.id))
//        _ <- localFile.streamToFile(fileStream, originalFile)
//        ocrFolder <- IO(new File(folder, "ocr"))
//        _ <- IO(ocrFolder.mkdirs())
//        ocrDestFile <- IO(new File(ocrFolder, receipt.id))
//        ocrStream <- ocrInt.tempGetOcrResult(receipt.userId, receipt.id)
//        _ <- localFile.streamToFile(ocrStream, ocrDestFile)
//        totalFolder <- IO(new File(folder, "total"))
//        _ <- IO(totalFolder.mkdirs())
//        totalDestFile <- IO(new File(totalFolder, receipt.id))
//        _ <- localFile.streamToFile(Stream.fromIterator[IO, Byte](total.toString.getBytes.toIterator), totalDestFile)
//        end = System.currentTimeMillis()
//        _ <- IO(println(s"OK: ${receipt.id} ${end - start}ms"))
//      } yield ()
//    }
//    case other => IO {
//      println(s"NOT PROCESSING ${receipt.id}, because $other")
//    }
//  }
//
//  val process = receipts.userReceipts(userId).flatMap(receipts => {
//
//    val sorted = receipts.sortBy(_.timestamp)
//    val toProcess = sorted.tail
//
//    val dest = new File("/home/leonti/Downloads/receipts/")
//    dest.mkdirs()
//    toProcess.traverse(processOcrReceipt(dest))
//  })
//
//  process.unsafeRunSync()
//
//}
