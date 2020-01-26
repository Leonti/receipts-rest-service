package config

import cats.effect.IO
import io.circe._
import io.circe.parser.decode
import io.circe.generic.auto._
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest

case class S3Config(region: String, bucket: String, accessKey: String, secretKey: String)

case class RoutingConfig(
    uploadsFolder: String,
    googleClientId: String,
    authTokenSecret: String
)

case class SearchConfig(
    baseUrl: String,
    apiKey: String
)

case class AppConfig(
    awsConfig: S3Config,
    routingConfig: RoutingConfig,
    env: String,
    searchConfig: SearchConfig,
    useOcrStub: Boolean,
    googleApiCredentials: String
)

object AppConfig {

  implicit val appConfigEncoder: Encoder[AppConfig] = deriveEncoder
  implicit val appConfigDecoder: Decoder[AppConfig] = deriveDecoder

  def readConfig(): IO[AppConfig] = IO {

    val credentials = new BasicAWSCredentials(sys.env("S3_ACCESS_KEY"), sys.env("S3_SECRET_ACCESS_KEY"))

    val ssm = AWSSimpleSystemsManagementClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
      .withRegion(sys.env("S3_REGION"))
      .build()

    val env     = sys.env("ENV")
    val request = new GetParameterRequest()
    request.setName(s"/receipts/$env")
    request.setWithDecryption(true);
    val param  = ssm.getParameter(request).getParameter.getValue
    val config = decode[AppConfig](param)
    config.toOption.get
  }

}
