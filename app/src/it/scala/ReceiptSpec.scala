import model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import UserTestUtils._
import ReceiptTestUtils._
import TestConfig._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import org.scalatest.time.{Millis, Seconds, Span}

class ReceiptSpec extends FlatSpec with Matchers with ScalaFutures with JsonProtocols {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(1000, Millis))
  implicit val system       = ActorSystem()
  implicit val materializer = ActorMaterializer()

  it should "create a receipt from an image" in {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val receiptEntityFuture = for {
      userInfo      <- createUser(createUserRequest)
      accessToken   <- authenticateUser(userInfo)
      requestEntity <- createImageFileContent()
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$appHostPort/user/${userInfo.id}/receipt",
          entity = requestEntity,
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      firstReceiptEntity <- Unmarshal(response.entity).to[ReceiptEntity]
      receiptEntity      <- getProcessedReceipt(userInfo.id, firstReceiptEntity.id, accessToken.accessToken)
    } yield receiptEntity

    whenReady(receiptEntityFuture) { receiptEntity =>
      receiptEntity.files.length shouldBe 2

      receiptEntity.total shouldBe ReceiptTestUtils.total
      receiptEntity.description shouldBe ReceiptTestUtils.description
      receiptEntity.transactionTime shouldBe ReceiptTestUtils.transactionTime
      receiptEntity.tags shouldBe ReceiptTestUtils.tags

      receiptEntity.files(0).metaData match {
        case ImageMetadata(fileType, length, width, height) =>
          width shouldBe 50
          height shouldBe 67
        //length shouldBe 5874
        case _ => fail("Metadata should be of an IMAGE type!")
      }
      receiptEntity.files(1).metaData match {
        case ImageMetadata(fileType, length, width, height) =>
          width shouldBe 50
          height shouldBe 67
        case _ => fail("Metadata should be of an IMAGE type!")
      }
    }
  }

  it should "reject receipt with the same file" in {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val errorResponseFuture = for {
      userInfo      <- createUser(createUserRequest)
      accessToken   <- authenticateUser(userInfo)
      requestEntity <- createImageFileContent()
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$appHostPort/user/${userInfo.id}/receipt",
          entity = requestEntity,
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      firstReceiptEntity <- Unmarshal(response.entity).to[ReceiptEntity]
      _                  <- getProcessedReceipt(userInfo.id, firstReceiptEntity.id, accessToken.accessToken)
      errorResponse <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$appHostPort/user/${userInfo.id}/receipt",
          entity = requestEntity,
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
    } yield errorResponse

    whenReady(errorResponseFuture) { errorResponse =>
      errorResponse.status shouldBe BadRequest
    }
  }

  it should "list receipts for a user" in {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val receiptsFuture = for {
      userInfo      <- createUser(createUserRequest)
      accessToken   <- authenticateUser(userInfo)
      requestEntity <- createTextFileContent("receipt content")
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$appHostPort/user/${userInfo.id}/receipt",
          entity = requestEntity,
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      receiptsResponse <- Http().singleRequest(
        HttpRequest(uri = s"$appHostPort/user/${userInfo.id}/receipt",
                    headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      receipts <- Unmarshal(receiptsResponse.entity).to[List[ReceiptEntity]]
    } yield receipts

    whenReady(receiptsFuture) { receipts =>
      receipts.length shouldBe 1
    }
  }

  it should "search for ocr results" in {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val receiptsFuture = for {
      userInfo      <- createUser(createUserRequest)
      accessToken   <- authenticateUser(userInfo)
      requestEntity <- createImageFileContent()
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$appHostPort/user/${userInfo.id}/receipt",
          entity = requestEntity,
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      firstReceiptEntity <- Unmarshal(response.entity).to[ReceiptEntity]
      _                  <- getProcessedReceipt(userInfo.id, firstReceiptEntity.id, accessToken.accessToken)
      receiptsResponse <- Http().singleRequest(
        HttpRequest(uri = s"$appHostPort/user/${userInfo.id}/receipt?q=ocr",
                    headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      receiptsNoResultsResponse <- Http().singleRequest(
        HttpRequest(uri = s"$appHostPort/user/${userInfo.id}/receipt?q=some_random_stuff",
                    headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      receipts          <- Unmarshal(receiptsResponse.entity).to[List[ReceiptEntity]]
      receiptsNoResults <- Unmarshal(receiptsNoResultsResponse.entity).to[List[ReceiptEntity]]
    } yield (receipts, receiptsNoResults)

    whenReady(receiptsFuture) {
      case (receipts, receiptsNoResults) =>
        receipts.length shouldBe 1
        receiptsNoResults.length shouldBe 0
    }
  }

  it should "add a file to existing receipt" in {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val receiptEntityFuture = for {
      userInfo        <- createUser(createUserRequest)
      accessToken     <- authenticateUser(userInfo)
      firstFileEntity <- createTextFileContent("first file")
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$appHostPort/user/${userInfo.id}/receipt",
          entity = firstFileEntity,
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      receiptEntity <- Unmarshal(response.entity).to[ReceiptEntity]

      secondFileEntity <- createTextFileContent("second file")
      addFilePendingFile <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$appHostPort/user/${userInfo.id}/receipt/${receiptEntity.id}/file",
          entity = secondFileEntity,
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      pendingFile          <- Unmarshal(addFilePendingFile.entity).to[PendingFile]
      updatedReceiptEntity <- getProcessedReceipt(userInfo.id, pendingFile.receiptId, accessToken.accessToken)
    } yield updatedReceiptEntity

    whenReady(receiptEntityFuture) { receiptEntity =>
      receiptEntity.files.length shouldBe 2
    }
  }

  it should "patch a receipt" in {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val receiptEntityFuture = for {
      userInfo        <- createUser(createUserRequest)
      accessToken     <- authenticateUser(userInfo)
      firstFileEntity <- createTextFileContent("first file")
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$appHostPort/user/${userInfo.id}/receipt",
          entity = firstFileEntity,
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      receiptEntity <- Unmarshal(response.entity).to[ReceiptEntity]

      _ <- {
        val patch = """[
                      |  {
                      |    "op": "replace",
                      |    "path": "/description",
                      |    "value": "some new description"
                      |  }
                      |]""".stripMargin
        Http().singleRequest(
          HttpRequest(
            method = HttpMethods.PATCH,
            uri = s"$appHostPort/user/${userInfo.id}/receipt/${receiptEntity.id}",
            entity = HttpEntity(`application/json`, patch),
            headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
          ))
      }

      secondResponse <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.GET,
          uri = s"$appHostPort/user/${userInfo.id}/receipt/${receiptEntity.id}",
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      updatedReceipt <- Unmarshal(secondResponse.entity).to[ReceiptEntity]
    } yield updatedReceipt

    whenReady(receiptEntityFuture) { receiptEntity =>
      receiptEntity.description shouldBe "some new description"
    }
  }

  it should "serve a file for a receipt" in {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val fileFuture = for {
      userInfo      <- createUser(createUserRequest)
      accessToken   <- authenticateUser(userInfo)
      requestEntity <- createTextFileContent("receipt content")
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$appHostPort/user/${userInfo.id}/receipt",
          entity = requestEntity,
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      initialReceiptEntity <- Unmarshal(response.entity).to[ReceiptEntity]
      receiptEntity        <- getProcessedReceipt(userInfo.id, initialReceiptEntity.id, accessToken.accessToken)
      fileResponse <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.GET,
          uri = s"$appHostPort/user/${userInfo.id}/receipt/${receiptEntity.id}/file/${receiptEntity.files(0).id}",
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      file <- Unmarshal(fileResponse.entity).to[String]
    } yield file

    whenReady(fileFuture) { file =>
      file should include("receipt content")
    }
  }

  it should "delete a receipt" in {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val receiptsFuture = for {
      userInfo      <- createUser(createUserRequest)
      accessToken   <- authenticateUser(userInfo)
      requestEntity <- createTextFileContent("receipt content")
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$appHostPort/user/${userInfo.id}/receipt",
          entity = requestEntity,
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      initialReceiptEntity <- Unmarshal(response.entity).to[ReceiptEntity]
      receiptEntity        <- getProcessedReceipt(userInfo.id, initialReceiptEntity.id, accessToken.accessToken)
      receiptsBeforeDeleteResponse <- Http().singleRequest(
        HttpRequest(uri = s"$appHostPort/user/${userInfo.id}/receipt",
                    headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      receiptsBeforeDelete <- Unmarshal(receiptsBeforeDeleteResponse.entity).to[List[ReceiptEntity]]
      _ <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.DELETE,
          uri = s"$appHostPort/user/${userInfo.id}/receipt/${receiptEntity.id}",
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      receiptsResponse <- Http().singleRequest(
        HttpRequest(uri = s"$appHostPort/user/${userInfo.id}/receipt",
                    headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      receipts <- Unmarshal(receiptsResponse.entity).to[List[ReceiptEntity]]
    } yield (receiptsBeforeDelete, receipts)

    whenReady(receiptsFuture) {
      case (receiptsBeforeDelete, receipts) => {
        receiptsBeforeDelete.length shouldBe 1
        receipts.length shouldBe 0
      }
    }
  }

}
