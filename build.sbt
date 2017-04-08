enablePlugins(JavaAppPackaging)

name         := "receipts-rest-service"
organization := "rocks.leonti"
version      := "1.0"
scalaVersion := "2.12.1"
scalacOptions += "-Ypartial-unification"
test in assembly := {}

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

val akkaV       = "2.4.17"
val akkaHttpV   = "10.0.5"
val amazonS3V   = "1.10.68"
val scalaTestV  = "3.0.1"
val reactiveMongoV = "0.12.1"
val jwtAuthV    = "0.3.0"
val visionApiV  = "v1-rev346-1.22.0"
val googleApiClient = "1.22.0"
val scalaLoggingV = "3.5.0"
val logbackV = "1.1.7"
val diffsonV = "2.1.2"

val logging = Seq (
  "ch.qos.logback" % "logback-classic" % logbackV,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,

  "org.slf4j" % "slf4j-api" % "1.7.12",
  "net.logstash.logback" % "logstash-logback-encoder" % "4.8",
  "com.typesafe.akka" %% "akka-slf4j" % akkaV
)

val EndToEndTest = config("e2e") extend(Test)
val e2eSettings =
  inConfig(EndToEndTest)(Defaults.testSettings) ++
    Seq(
      fork in EndToEndTest := false,
      parallelExecution in EndToEndTest := false,
      scalaSource in EndToEndTest := baseDirectory.value / "src/e2e/scala")

libraryDependencies ++= {
  Seq(
    "com.typesafe.akka" %% "akka-actor"                           % akkaV,
    "com.typesafe.akka" %% "akka-stream"                          % akkaV,
    "com.typesafe.akka" %% "akka-http-core"                       % akkaHttpV,
    "com.typesafe.akka" %% "akka-http"                            % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-spray-json"                 % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-testkit"                    % akkaHttpV,
    "com.amazonaws"     %   "aws-java-sdk-s3"                     % amazonS3V,
    "com.google.apis"   % "google-api-services-vision"            % visionApiV,
    "com.google.api-client" % "google-api-client"                 % googleApiClient,
    "org.reactivemongo" %% "reactivemongo"                        % reactiveMongoV excludeAll(
      ExclusionRule(organization = "com.typesafe.akka")
      ),
    "com.drewnoakes"    %  "metadata-extractor"                   % "2.9.0",
    "de.choffmeister"   % "auth-common_2.11"                          % jwtAuthV excludeAll(
      ExclusionRule(organization = "io.spray")
      ),
    "de.choffmeister"   % "auth-akka-http_2.11"                       % jwtAuthV excludeAll(
      ExclusionRule(organization = "com.typesafe.akka"),
      ExclusionRule(organization = "io.spray")
      ),
    "org.gnieh" %% "diffson-spray-json"                           % diffsonV,
    "org.scalatest"     %% "scalatest"                            % scalaTestV % "it,test",
    "org.mockito"       %  "mockito-all"                          % "1.8.4" % "test"
  )
}

libraryDependencies ++= logging

resolvers ++= Seq(
  Resolver.bintrayRepo("dwhjames", "maven")
)

resolvers += Resolver.bintrayRepo("choffmeister", "maven")

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "Typesafe" at "https://repo.typesafe.com/typesafe/releases/"

//val userHome = System.getProperty("user.home")
//javaOptions +=  s"-Dconfig.file=${userHome}/.receipts-rest-service/service.conf"

Revolver.settings

lazy val integrate = taskKey[Unit]("Starts REST API server and runs integration tests")

lazy val preIntegrationTests = taskKey[Unit]("Starts REST API server and runs integration tests")

preIntegrationTests := {
  val cp: Seq[File] = (fullClasspath in IntegrationTest).value.files
  AppRunnerRemoteControl.setClassPath(cp)
  AppRunnerRemoteControl.setLog(streams.value.log)
}

integrate := {
  preIntegrationTests.value
  (test in IntegrationTest).value
}