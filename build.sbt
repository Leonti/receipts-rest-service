enablePlugins(JavaAppPackaging)

name         := "receipts-rest-service"
organization := "rocks.leonti"
version      := "1.0"
scalaVersion := "2.11.7"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

val logging = Seq (
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.1"
)

libraryDependencies ++= {
  val akkaV       = "2.4.1"
  val akkaStreamV = "2.0.1"
  val scalaTestV  = "2.2.5"
  val commonsAwsV = "0.9.0"
  val reactiveMongoV = "0.11.9"
  Seq(
    "com.typesafe.akka" %% "akka-actor"                           % akkaV,
    "com.typesafe.akka" %% "akka-stream-experimental"             % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental"          % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-experimental"               % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental"    % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-testkit-experimental"       % akkaStreamV,
    "com.mfglabs"       %% "commons-aws"                          % commonsAwsV,
    "org.reactivemongo" %% "reactivemongo"                        % reactiveMongoV,
    "de.choffmeister"   %% "auth-common"                          % "0.1.0",
    "de.choffmeister"   %% "auth-akka-http"                       % "0.1.0",
    "org.scalatest"     %% "scalatest"                            % scalaTestV % "test",
    "org.mockito"       %  "mockito-all"                          % "1.8.4" % "test"
  )
}

libraryDependencies ++= logging

resolvers ++= Seq(
  Resolver.bintrayRepo("mfglabs", "maven"),
  Resolver.bintrayRepo("dwhjames", "maven")
)

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
resolvers += "Typesafe" at "https://repo.typesafe.com/typesafe/releases/"

val userHome = System.getProperty("user.home")
javaOptions +=  s"-Dconfig.file=${userHome}/.receipts-rest-service/service.conf"

Revolver.settings
