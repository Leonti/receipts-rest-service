package model

import de.choffmeister.auth.common.OAuth2AccessTokenResponseFormat
import spray.json.{DefaultJsonProtocol, NullOptions}

trait JsonProtocols extends DefaultJsonProtocol with NullOptions {
  implicit val fileMetadataFormat  = FileMetadataFormat
  implicit val fileEntityFormat    = jsonFormat5(FileEntity.apply)
  implicit val receiptEntityFormat = jsonFormat9(ReceiptEntity.apply)
  implicit val createUserFormat    = jsonFormat2(CreateUserRequest.apply)
  implicit val userInfoFormat      = jsonFormat2(UserInfo.apply)
  implicit val errorResponseFormat = jsonFormat1(ErrorResponse.apply)
  implicit val okResponseFormat    = jsonFormat1(OkResponse.apply)
  implicit val jwtTokenFormat      = OAuth2AccessTokenResponseFormat
  implicit val pendingFileFormat   = jsonFormat3(PendingFile.apply)
}
