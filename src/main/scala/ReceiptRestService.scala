import java.util.Date

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Credentials`, `Access-Control-Allow-Headers`, `Access-Control-Max-Age`}
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
import repository.{ReceiptRepository, UserRepository}
import routing.{AuthenticationRouting, CorsSupport, ReceiptRouting, UserRouting}
import service.{FileService, ReceiptService, UserService}

import scala.collection.mutable
import scala.concurrent.{ExecutionContextExecutor, Future}
import spray.json._
import de.choffmeister.auth.akkahttp.Authenticator
import de.choffmeister.auth.common._

import scala.concurrent.duration._
import scala.util.Try
import scala.util.Success
import scala.util.Failure

// http://bandrzejczak.com/blog/2015/12/06/sso-for-your-single-page-application-part-2-slash-2-akka-http/

trait Service extends JsonProtocols with CorsSupport {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  override val corsAllowOrigins: List[String] = List("*")
  override val corsAllowedHeaders: List[String] = List(
    "Origin",
    "X-Requested-With",
    "Content-Type",
    "Accept",
    "Accept-Encoding",
    "Accept-Language",
    "Host",
    "Referer",
    "User-Agent",
    "Authorization")
  override val corsAllowCredentials: Boolean = true
  override val optionsCorsHeaders: List[HttpHeader] = List[HttpHeader](
    `Access-Control-Allow-Headers`(corsAllowedHeaders.mkString(", ")),
    `Access-Control-Max-Age`(60 * 60 * 24 * 20), // cache pre-flight response for 20 days
    `Access-Control-Allow-Credentials`(corsAllowCredentials)
  )

  def config: Config
  val logger: LoggingAdapter

  val userRouting: UserRouting
  val receiptRouting: ReceiptRouting
  val authenticationRouting: AuthenticationRouting

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
    logRequest("receipt-rest-service") {
      logRequestResult("receipt-rest-service") {
        handleRejections(myRejectionHandler) {
          cors {
            userRouting.routes ~ // http://bandrzejczak.com/blog/2015/12/06/sso-for-your-single-page-application-part-2-slash-2-akka-http/
              receiptRouting.routes ~
              authenticationRouting.routes ~
              path("version") {
                get {
                  complete(Created -> System.getenv("VERSION"))
                }
              }
          }
        }
      }
    }
  }
}

object ReceiptRestService extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  val userService = new UserService(new UserRepository())

  val authenticator = new Authenticator[User](
    realm = "Example realm",
    bearerTokenSecret = "secret-no-one-knows".getBytes,
    findUserById = userService.findById,
    findUserByUserName = userService.findByUserName,
    validateUserPassword = userService.validatePassword)

  override val config = ConfigFactory.load()

  println("Mongo:")
  println(config.getString("mongodb.db"))

  override val logger = Logging(system, getClass)
  override val receiptRouting = new ReceiptRouting(new ReceiptService(new ReceiptRepository()), FileService.s3(config, materializer), authenticator.bearerToken(acceptExpired = true))
  override val userRouting = new UserRouting(userService, authenticator.bearerToken(acceptExpired = true))
  override val authenticationRouting = new AuthenticationRouting(authenticator)

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
