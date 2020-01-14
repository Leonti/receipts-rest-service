package object TestConfig {
  val appHostPort          = sys.env("APP_HOST_PORT")
  val auth0ApiClientId     = sys.env("AUTH0_API_CLIENT_ID")
  val auth0ApiClientSecret = sys.env("AUTH0_CLIENT_SECRET")
  val auth0ApiAudience     = "https://leonti.au.auth0.com/api/v2/"
  val auth0BaseUrl: String = auth0ApiAudience
  val auth0ConnectionName  = "Username-Password-Authentication"
}
