//enablePlugins(JavaAppPackaging)

name := "receipts-rest-service"
organization := "rocks.leonti"
version := "1.0"
scalaVersion := "2.12.4"
test in assembly := {}

scalacOptions := Seq("-unchecked",
                     "-deprecation",
                     "-feature",
                     "-Xfatal-warnings",
                     "-Ywarn-unused-import",
                     "-Ypartial-unification",
                     "-encoding",
                     "utf8")

addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.3" cross CrossVersion.binary)

val akkaV           = "2.4.20"
val akkaHttpV       = "10.0.11"
val amazonS3V       = "1.11.241"
val scalaTestV      = "3.0.4"
val reactiveMongoV  = "0.12.6"
val jwtAuthV        = "0.3.0-1-g71ae99b"
val visionApiV      = "v1-rev346-1.22.0"
val googleApiClient = "1.22.0"
val scalaLoggingV   = "3.5.0"
val logbackV        = "1.1.7"
val diffsonV        = "2.1.2"
val catsV           = "0.9.0"
val freekV          = "0.6.7"

val logging = Seq(
  "ch.qos.logback"             % "logback-classic"          % logbackV,
  "com.typesafe.scala-logging" %% "scala-logging"           % scalaLoggingV,
  "org.slf4j"                  % "slf4j-api"                % "1.7.12",
  "net.logstash.logback"       % "logstash-logback-encoder" % "4.8",
  "com.typesafe.akka"          %% "akka-slf4j"              % akkaV
)

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings
  )

libraryDependencies ++= {
  Seq(
    "com.typesafe.akka"     %% "akka-actor"                % akkaV,
    "com.typesafe.akka"     %% "akka-stream"               % akkaV,
    "com.typesafe.akka"     %% "akka-http-core"            % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http"                 % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http-spray-json"      % akkaHttpV,
    "com.typesafe.akka"     %% "akka-http-testkit"         % akkaHttpV,
    "com.amazonaws"         % "aws-java-sdk-s3"            % amazonS3V,
    "com.google.apis"       % "google-api-services-vision" % visionApiV,
    "com.google.api-client" % "google-api-client"          % googleApiClient,
    "org.reactivemongo"     %% "reactivemongo"             % reactiveMongoV excludeAll (
      ExclusionRule(organization = "com.typesafe.akka")
    ),
    "com.drewnoakes"       % "metadata-extractor" % "2.9.0",
    "org.typelevel"        %% "cats"              % catsV,
    "com.projectseptember" %% "freek"             % freekV,
    "de.choffmeister"      %% "auth-common"       % jwtAuthV excludeAll (
      ExclusionRule(organization = "io.spray")
    ),
    "de.choffmeister" %% "auth-akka-http" % jwtAuthV excludeAll (
      ExclusionRule(organization = "com.typesafe.akka"),
      ExclusionRule(organization = "io.spray")
    ),
    "org.gnieh"     %% "diffson-spray-json" % diffsonV,
    "org.scalatest" %% "scalatest"          % scalaTestV % "it,test"//,
//    "org.mockito"   % "mockito-all"         % "1.10.19" % "test"
  )
}

libraryDependencies ++= logging

resolvers ++= Seq(
  Resolver.bintrayRepo("dwhjames", "maven")
)

resolvers += Resolver.bintrayRepo("projectseptemberinc", "maven")

// auth-utils is published there
resolvers += Resolver.bintrayRepo("leonti", "maven")

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "Typesafe" at "https://repo.typesafe.com/typesafe/releases/"


//Revolver.settings

mainClass in assembly := Some("ReceiptRestService")
