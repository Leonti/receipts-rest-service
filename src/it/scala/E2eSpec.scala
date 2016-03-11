import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.Multipart.FormData.Strict
import akka.http.scaladsl.model.headers.{OAuth2BearerToken, BasicHttpCredentials, Authorization}
import de.choffmeister.auth.common.OAuth2AccessTokenResponse
import model.{ReceiptEntity, CreateUserRequest, UserInfo, JsonProtocols}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}

import akka.http.scaladsl.unmarshalling.Unmarshal

import akka.http.scaladsl.marshalling.Marshal

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.concurrent.Future

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import scala.concurrent.ExecutionContext.Implicits.global

class E2eSpec extends FlatSpec with Matchers with ScalaFutures  with JsonProtocols {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(15, Seconds), interval = Span(500, Millis))

  def createUser(createUserRequest: CreateUserRequest): Future[UserInfo] = {
    for {
      request <- Marshal(createUserRequest).to[RequestEntity]
      response <- Http().singleRequest(HttpRequest(method = HttpMethods.POST, uri = s"http://localhost:9000/user/create", entity = request))
      userInfo <- Unmarshal(response.entity).to[UserInfo]
    } yield userInfo
  }

  def authenticateUser(userInfo: UserInfo): Future[OAuth2AccessTokenResponse] = {
    for {
      response <- Http().singleRequest(HttpRequest(uri = s"http://localhost:9000/token/create",
        headers = List(Authorization(BasicHttpCredentials(userInfo.userName, "password")))))
      accessToken <- Unmarshal(response.entity).to[OAuth2AccessTokenResponse]
    } yield accessToken
  }

  it should "create a user" in {

    val username = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    whenReady(createUser(createUserRequest)) { userInfo =>

      userInfo.userName shouldBe username
    }
  }

  it should "authenticate a user" in {
    val username = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    whenReady(createUser(createUserRequest).flatMap(authenticateUser(_))) { accessToken =>
      accessToken.expiresIn shouldBe 3600000
    }
  }

  it should "renew token" in {
    val username = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val renewedTokenFuture = for {
      userInfo <- createUser(createUserRequest)
      accessToken <- authenticateUser(userInfo)
      response <- Http().singleRequest(HttpRequest(uri = s"http://localhost:9000/token/renew",
        headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      renewedToken <- Unmarshal(response.entity).to[OAuth2AccessTokenResponse]
    } yield renewedToken

    whenReady(renewedTokenFuture) { renewedToken =>
      renewedToken.expiresIn shouldBe 3600000
    }
  }

  def createTextFileContent(text: String): Future[RequestEntity] = {
    val content = text.getBytes
    val multipartForm =
      Multipart.FormData(Multipart.FormData.BodyPart.Strict(
        "receipt",
        HttpEntity(`application/octet-stream`, content),
        Map("filename" -> "receipt.txt")))
    Marshal(multipartForm).to[RequestEntity]
  }

  it should "create a receipt from a file" in {
    val username = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val receiptEntityFuture = for {
      userInfo <- createUser(createUserRequest)
      accessToken <- authenticateUser(userInfo)
      requestEntity <- createTextFileContent("receipt content")
      response <- Http().singleRequest(HttpRequest(method = HttpMethods.POST,
        uri = s"http://localhost:9000/user/${userInfo.id}/receipt",
        entity = requestEntity,
        headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      receiptEntity <- Unmarshal(response.entity).to[ReceiptEntity]
    } yield receiptEntity

    whenReady(receiptEntityFuture) { receiptEntity =>
      receiptEntity.files.length shouldBe 1
    }
  }

  it should "list receipts for a user" in {
    val username = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val receiptsFuture = for {
      userInfo <- createUser(createUserRequest)
      accessToken <- authenticateUser(userInfo)
      requestEntity <- createTextFileContent("receipt content")
      response <- Http().singleRequest(HttpRequest(method = HttpMethods.POST,
        uri = s"http://localhost:9000/user/${userInfo.id}/receipt",
        entity = requestEntity,
        headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      receiptsResponse <- Http().singleRequest(HttpRequest(
        uri = s"http://localhost:9000/user/${userInfo.id}/receipt",
        headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      receipts <- Unmarshal(receiptsResponse.entity).to[List[ReceiptEntity]]
    } yield receipts

    whenReady(receiptsFuture) { receipts =>
      receipts.length shouldBe 1
    }
  }

  it should "add a file to existing receipt" in {
    val username = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val receiptEntityFuture = for {
      userInfo <- createUser(createUserRequest)
      accessToken <- authenticateUser(userInfo)
      firstFileEntity <- createTextFileContent("first file")
      response <- Http().singleRequest(HttpRequest(method = HttpMethods.POST,
        uri = s"http://localhost:9000/user/${userInfo.id}/receipt",
        entity = firstFileEntity,
        headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      receiptEntity <- Unmarshal(response.entity).to[ReceiptEntity]

      secondFileEntity <- createTextFileContent("second file")
      secondResponse <- Http().singleRequest(HttpRequest(method = HttpMethods.POST,
        uri = s"http://localhost:9000/user/${userInfo.id}/receipt/${receiptEntity.id}/file",
        entity = secondFileEntity,
        headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      updatedReceipt <- Unmarshal(secondResponse.entity).to[ReceiptEntity]
    } yield updatedReceipt

    whenReady(receiptEntityFuture) { receiptEntity =>
      receiptEntity.files.length shouldBe 2
    }
  }

  it should "serve a file for a receipt" in {
    val username = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val fileFuture = for {
      userInfo <- createUser(createUserRequest)
      accessToken <- authenticateUser(userInfo)
      requestEntity <- createTextFileContent("receipt content")
      response <- Http().singleRequest(HttpRequest(method = HttpMethods.POST,
        uri = s"http://localhost:9000/user/${userInfo.id}/receipt",
        entity = requestEntity,
        headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      receiptEntity <- Unmarshal(response.entity).to[ReceiptEntity]
      fileResponse <- Http().singleRequest(HttpRequest(method = HttpMethods.GET,
        uri = s"http://localhost:9000/user/${userInfo.id}/receipt/${receiptEntity.id}/file/${receiptEntity.files(0).id}",
        headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      file <-  Unmarshal(fileResponse.entity).to[String]
    } yield file

    whenReady(fileFuture) { file =>
      file should include("receipt content")
    }
  }
}
