import sbt.Defaults.testSettings
import sbt.librarymanagement.Configuration
import sbt.librarymanagement.Configurations.Runtime
//enablePlugins(JavaAppPackaging)

name := "receipts-rest-service"
organization := "rocks.leonti"
version := "1.0"
scalaVersion := "2.12.4"
test in assembly := {}

scalacOptions := Seq("-unchecked",
                     "-deprecation",
                     "-feature",
                     "-language:higherKinds",
                     "-Xfatal-warnings",
                     "-Ywarn-dead-code",
                     "-Ywarn-inaccessible",
                     "-Ywarn-unused",
                     "-Ywarn-unused-import",
                     "-Ypartial-unification",
                     "-encoding",
                     "utf8")

addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.3" cross CrossVersion.binary)

val awsV       = "1.11.241"
val scalaTestV      = "3.0.4"
val visionApiV      = "v1-rev346-1.22.0"
val googleApiClient = "1.22.0"
val scalaLoggingV   = "3.5.0"
val logbackV        = "1.1.7"
val diffsonV        = "3.0.0"
val catsV           = "1.6.0"
val circeVersion = "0.11.1"
val fs2V = "1.0.3"
val http4sVersion = "0.20.0-M5"
val scanamoV = "1.0.0-M9"

val logging = Seq(
  "ch.qos.logback"             % "logback-classic"          % logbackV,
  "com.typesafe.scala-logging" %% "scala-logging"           % scalaLoggingV,
  "org.slf4j"                  % "slf4j-api"                % "1.7.12",
  "net.logstash.logback"       % "logstash-logback-encoder" % "4.8"
)

lazy val End2EndTest = Configuration.of("End2EndTest", "e2e") extend Runtime
lazy val root = (project in file("."))
  .configs(IntegrationTest, End2EndTest)
  .settings(
    Defaults.itSettings,
    inConfig(End2EndTest)(testSettings)
  )

libraryDependencies ++= {
  Seq(
    "com.amazonaws"         % "aws-java-sdk-s3"            % awsV,
    "com.amazonaws"         % "aws-java-sdk-sqs"            % awsV,
    "com.google.apis"       % "google-api-services-vision" % visionApiV excludeAll (
      ExclusionRule(organization="com.google.guava", name="guava-jdk5")
      ),
    "com.google.api-client" % "google-api-client"          % googleApiClient excludeAll (
      ExclusionRule(organization="com.google.guava", name="guava-jdk5")
      ),
    "com.drewnoakes"       % "metadata-extractor" % "2.9.0",
    "org.typelevel"        %% "cats-core"              % catsV,
    "org.gnieh"     %% "diffson-circe" % diffsonV,
    "com.auth0" % "java-jwt" % "3.4.0" excludeAll (
      ExclusionRule(organization = "com.fasterxml.jackson.core")
      ),
    "com.auth0" % "jwks-rsa" % "0.6.0",
    "org.scanamo" %% "scanamo" % scanamoV,
    "org.scanamo" %% "scanamo-cats-effect" % scanamoV,
    "org.scanamo" %% "scanamo-testkit" % scanamoV,
    //"io.github.howardjohn" %% "scanamo-circe" % "0.2.1",
    "co.fs2" %% "fs2-core" % fs2V,
    "co.fs2" %% "fs2-io" % fs2V,
    "org.scalatest" %% "scalatest"          % scalaTestV % "e2e,it,test"
  )
}

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies ++= logging

resolvers ++= Seq(
  Resolver.bintrayRepo("dwhjames", "maven")
)

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
resolvers += "Typesafe" at "https://repo.typesafe.com/typesafe/releases/"

updateOptions := updateOptions.value.withLatestSnapshots(false)

mainClass in assembly := Some("ReceiptRestService")

assemblyMergeStrategy in assembly := {
  case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
