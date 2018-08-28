package authentication

import io.circe.{Decoder, Encoder, HCursor, Json}

case class OAuth2AccessTokenResponse(tokenType: String, accessToken: String, expiresIn: Long)

object OAuth2AccessTokenResponse {

  implicit val encodeToken: Encoder[OAuth2AccessTokenResponse] = new Encoder[OAuth2AccessTokenResponse] {
    final def apply(a: OAuth2AccessTokenResponse): Json = Json.obj(
      ("token_type", Json.fromString(a.tokenType)),
      ("access_token", Json.fromString(a.accessToken)),
      ("expires_in", Json.fromLong(a.expiresIn))
    )
  }

  implicit val decodeToken: Decoder[OAuth2AccessTokenResponse] = new Decoder[OAuth2AccessTokenResponse] {
    final def apply(c: HCursor): Decoder.Result[OAuth2AccessTokenResponse] =
      for {
        tokenType   <- c.downField("token_type").as[String]
        accessToken <- c.downField("access_token").as[String]
        expiresIn   <- c.downField("expires_in").as[Long]
      } yield {
        OAuth2AccessTokenResponse(tokenType, accessToken, expiresIn)
      }
  }

}
