import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, OAuth2BearerToken}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.util.ByteString
import de.choffmeister.auth.common.OAuth2AccessTokenResponse
import model.{CreateUserRequest, JsonProtocols, UserInfo}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.BufferedSource
import scala.util.{Failure, Success}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.ConfigFactory

import scala.collection.immutable.Seq

class Uploader extends JsonProtocols {
  implicit val system       = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec           = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(200))

  def createUser(createUserRequest: CreateUserRequest): Future[UserInfo] = {
    for {
      request <- Marshal(createUserRequest).to[RequestEntity]
      response <- Http().singleRequest(
        HttpRequest(method = HttpMethods.POST, uri = s"http://localhost:9000/user/create", entity = request))
      userInfo <- Unmarshal(response.entity).to[UserInfo]
    } yield userInfo
  }

  def authenticateUser(userInfo: UserInfo): Future[OAuth2AccessTokenResponse] = {
    for {
      response <- Http().singleRequest(
        HttpRequest(uri = s"http://localhost:9000/token/create",
                    headers = List(Authorization(BasicHttpCredentials(userInfo.userName, "password")))))
      accessToken <- Unmarshal(response.entity).to[OAuth2AccessTokenResponse]
    } yield accessToken
  }

  def utf8TextEntity(content: String) = {
    val bytes = ByteString(content)
    HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, bytes)
  }

  def createImageFileContent(): Future[RequestEntity] = {
    val receiptImage: BufferedSource = scala.io.Source.fromFile("/home/leonti/Downloads/receipt.jpg", "ISO-8859-1")
    val content                      = receiptImage.map(_.toByte).toArray

    val multipartForm =
      Multipart.FormData(
        Multipart.FormData.BodyPart.Strict("receipt", HttpEntity(`application/octet-stream`, content), Map("filename" -> "receipt.png")),
        Multipart.FormData.BodyPart.Strict("total", utf8TextEntity("12.38")),
        Multipart.FormData.BodyPart.Strict("description", utf8TextEntity("some description"))
      )
    Marshal(multipartForm).to[RequestEntity]
  }

  val toReceiptRequest: (MessageEntity, String, String, String) => HttpRequest = (requestEntity, baseUrl, userId, accessToken) => {
    HttpRequest(method = HttpMethods.POST,
                uri = s"${baseUrl}/user/${userId}/receipt",
                entity = requestEntity,
                headers = List(Authorization(OAuth2BearerToken(accessToken))))
  }

  def upload() = {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val uploadReceipt: (HttpRequest) => Future[StatusCode] = request => {
      val start = System.currentTimeMillis()
      println("Starting to upload receipt")

      val config = ConfigFactory.load()

      //  println("max connections " + ConfigFactory.load().getInt("akka.http.client.host-connection-pool.max-connections"))

      Http(system)
        .singleRequest(request)
        .map(response => {
          println(response.status)
          val end = System.currentTimeMillis()
          println(s"Receipt uploaded in ${(end - start)}ms")
          response.status
        })
    }

    val requests: Future[Seq[HttpRequest]] = for {
      userInfo: UserInfo                     <- createUser(createUserRequest)
      accessToken: OAuth2AccessTokenResponse <- authenticateUser(userInfo)
      requestEntity: MessageEntity           <- createImageFileContent()
    } yield Seq.fill(10)(toReceiptRequest(requestEntity, "http://localhost:9000", userInfo.id, accessToken.accessToken))

    val result: Future[Seq[StatusCode]] = requests.flatMap(requests => Future.sequence(requests.map(request => uploadReceipt(request))))
    result
  }

  def uploadWithFlow() = {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val requestsFuture: Future[Seq[HttpRequest]] = for {
      userInfo: UserInfo                     <- createUser(createUserRequest)
      accessToken: OAuth2AccessTokenResponse <- authenticateUser(userInfo)
      requestEntity: MessageEntity           <- createImageFileContent()
    } yield Seq.fill(10)(toReceiptRequest(requestEntity, "", userInfo.id, accessToken.accessToken))

    val statusPrinterFlow = Flow[HttpResponse].map(httpResponse => {
      println(httpResponse.status)
      println(s"Response ${httpResponse.entity.toString}")

      httpResponse
    })

    val result: Future[Seq[HttpResponse]] = requestsFuture.flatMap(requests => {
      val res: Future[Seq[HttpResponse]] = Future.sequence(
        requests
          .map(httpRequest => {

            val requestFlow = Http().outgoingConnection("localhost", 9000)

            Source
              .single[HttpRequest](httpRequest)
              .via(requestFlow)
              .via(statusPrinterFlow)
              .runWith(Sink.head)
          }))

      res
    })

    result
  }

}

object LoadTest {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(200))

  def main1(args: Array[String]): Unit = {

    val start = System.currentTimeMillis()

    new Uploader()
      .upload()
      .onComplete({
        case Success(receipt) => {
          val end      = System.currentTimeMillis()
          val toUpload = end - start
          println(s"All receipts uploaded in ${toUpload / 1000}s")
        }
        case Failure(e) => println(s"Exception happened! ${e} ${e.getStackTrace.foreach(e => println(e))}")
      })

  }
}
