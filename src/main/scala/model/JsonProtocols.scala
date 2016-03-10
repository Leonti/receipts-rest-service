package model

import de.choffmeister.auth.common.OAuth2AccessTokenResponseFormat
import spray.json.DefaultJsonProtocol

trait JsonProtocols extends DefaultJsonProtocol {
  implicit val fileEntityFormat = jsonFormat3(FileEntity.apply)
  implicit val receiptEntityFormat = jsonFormat6(ReceiptEntity.apply)
  implicit val createUserFormat = jsonFormat2(CreateUserRequest.apply)
  implicit val userInfoFormat = jsonFormat2(UserInfo.apply)
  implicit val errorResponseFormat = jsonFormat1(ErrorResponse.apply)
  implicit val okResponseFormat = jsonFormat1(OkResponse.apply)
  implicit val jwtTokenFormat = OAuth2AccessTokenResponseFormat
}
