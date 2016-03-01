import java.util.Date

import akka.actor.ActorSystem
import akka.event.{LoggingAdapter, Logging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl._
import akka.util.ByteString
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import model.ReceiptEntity
import model._
import scala.concurrent.{ExecutionContextExecutor, Future}
import spray.json._

import de.choffmeister.auth.akkahttp.Authenticator
import de.choffmeister.auth.common._
import scala.concurrent.duration._

import scala.util.Try
import scala.util.Success
import scala.util.Failure

// http://bandrzejczak.com/blog/2015/12/06/sso-for-your-single-page-application-part-2-slash-2-akka-http/

trait Protocols extends DefaultJsonProtocol {
  implicit val receiptEntityFormat = jsonFormat6(ReceiptEntity.apply)
  implicit val createUserFormat = jsonFormat2(CreateUserRequest.apply)
  implicit val userInfoFormat = jsonFormat2(UserInfo.apply)
  implicit val errorResponseFormat = jsonFormat1(ErrorResponse.apply)
  implicit val okResponseFormat = jsonFormat1(OkResponse.apply)
  implicit val jwtTokenFormat = OAuth2AccessTokenResponseFormat
}

trait Service extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config
  val logger: LoggingAdapter

  val receiptService: ReceiptService
  val userService: UserService
  val fileService: FileService
  val authenticator: Authenticator[User]

  val bearerTokenSecret: Array[Byte]
  val bearerTokenLifetime: FiniteDuration

  def myRejectionHandler =
    RejectionHandler.newBuilder()
      .handle { case AuthorizationFailedRejection =>
        complete((Forbidden -> ErrorResponse("Access forbidden")))
      }
      .handle { case MissingFormFieldRejection(field) =>
        complete((BadRequest -> ErrorResponse(s"Request is missing required form field '${field}'")))
      }
        .handle { case AuthenticationFailedRejection(cause, challenge) =>
          complete((Unauthorized -> ErrorResponse("The supplied authentication is invalid")))
        }
      .result()

  val routes = {
    logRequestResult("akka-http-microservice") {
      handleRejections(myRejectionHandler) {
        pathPrefix("user" / "create") { // curl -H "Content-Type: application/json" -i -X POST -d '{"userName": "leonti3", "password": "pass1"}' http://localhost:9000/user/create
          (post & entity(as[CreateUserRequest])) { createUserRequest =>

            val userFuture: Future[Either[String, User]] = userService.createUser(createUserRequest)
            onComplete(userFuture) { (result: Try[Either[String, User]]) =>
              result match {
                case Success(user: Either[String, User]) => user match {
                  case Right(user) => complete(Created -> UserInfo(user))
                  case Left(error) => complete(Conflict -> ErrorResponse(s"error creating user: ${error}"))
                }
                case Failure(t: Throwable) => complete(InternalServerError -> ErrorResponse(s"server failure: ${t}"))
              }
            }

          }
        } ~ // http://bandrzejczak.com/blog/2015/12/06/sso-for-your-single-page-application-part-2-slash-2-akka-http/
        pathPrefix("user" / Segment) { userId: String =>
          authenticator.bearerToken(acceptExpired = true) { user =>
            authorize(user.id == userId) {
              pathPrefix("receipt") {
                get {
                  extractRequest { request =>

                    val userReceiptsFuture = receiptService.findForUserId(userId)
                    complete {
                      userReceiptsFuture.map[ToResponseMarshallable](receipts => receipts)
                    }
                  }
                } ~
                  post { //curl -X POST -H 'Content-Type: application/octet-stream' -d @test.txt http://localhost:9000/leonti/receipt
                    fileUpload("receipt") {
                      case (metadata: FileInfo, byteSource: Source[ByteString, Any]) =>
                        val fileUploadFuture: Future[String] = fileService.save(byteSource)

                        val receiptIdFuture: Future[ReceiptEntity] = fileUploadFuture.flatMap((fileId: String) => receiptService.createReceipt(
                          userId = userId, fileId = fileId
                        ))

                        onComplete(receiptIdFuture) { receipt =>
                          complete(Created -> receipt)
                        }
                    }
                  } ~
                  path(Segment / "file") { receiptId: String =>
                    post {
                      println("adding file to receipt")
                      fileUpload("receipt") {
                        case (metadata: FileInfo, byteSource: Source[ByteString, Any]) =>
                          val fileUploadFuture: Future[String] = fileService.save(byteSource)

                          val receiptFuture: Future[Option[ReceiptEntity]] = fileUploadFuture.flatMap((fileId: String) => receiptService.addFileToReceipt(receiptId, fileId))

                          onComplete(receiptFuture) { (result: Try[Option[ReceiptEntity]]) =>

                            result match {
                              case Success(receiptResult: Option[ReceiptEntity]) => receiptResult match {
                                case Some(receipt) => complete(Created -> receipt)
                                case None => complete(BadRequest -> ErrorResponse(s"Receipt ${receiptId} doesn't exist"))
                              }
                              case Failure(t: Throwable) => complete(InternalServerError -> ErrorResponse(s"server failure: ${t}"))
                            }
                          }
                      }
                    }
                  }
              }
            }
          }
        }~
        pathPrefix("token" / "create") { // curl -X GET -u "user2:pass2" http://localhost:9000/token/create
          get {
            // Here we can send valid username/password HTTP basic authentication
            // and get a JWT for it. If wrong credentials were given, then this
            // route is not completed before 1 second has passed. This makes timing
            // attacks harder, since an attacker cannot distinguish between wrong
            // username and existing username, but wrong password.
            authenticator.basic(Some(1000.millis))(user => completeWithToken(user, bearerTokenSecret, bearerTokenLifetime))
          }
        } ~
        path("token" / "renew") {
          get {
            // Here we can send an expired JWT via HTTP bearer authentication and
            // get a renewed JWT for it.
            authenticator.bearerToken(acceptExpired = true)(user => completeWithToken(user, bearerTokenSecret, bearerTokenLifetime))
          }
        }

      }
    }
  }

  private def completeWithToken(user: User, secret: Array[Byte], lifetime: FiniteDuration): Route = {
    val now = System.currentTimeMillis / 1000L * 1000L

    val token = JsonWebToken(
      createdAt = new Date(now),
      expiresAt = new Date(now + lifetime.toMillis * 1000L),
      subject = user.id.toString,
      claims = Map("name" -> JsString(user.userName))
    )
    val tokenStr = JsonWebToken.write(token, secret)

    complete(OAuth2AccessTokenResponse("bearer", tokenStr, lifetime.toMillis))
  }
}

object ReceiptRestService extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val bearerTokenSecret: Array[Byte] = "secret-no-one-knows".getBytes
  override val bearerTokenLifetime: FiniteDuration = 60.minutes

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)
  override val receiptService = new ReceiptService(new ReceiptRepository())
  override val userService = new UserService(new UserRepository())
  override val fileService = new FileService(config, materializer)
  override val authenticator = new Authenticator[User](
    realm = "Example realm",
    bearerTokenSecret = bearerTokenSecret,
    findUserById = userService.findById,
    findUserByUserName = userService.findByUserName,
    validateUserPassword = userService.validatePassword)

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
