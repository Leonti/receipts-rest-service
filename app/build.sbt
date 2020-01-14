import sbt.Defaults.testSettings
import sbt.librarymanagement.Configuration
import sbt.librarymanagement.Configurations.Runtime
import bloop.integrations.sbt.BloopDefaults
import org.scalafmt.sbt.ScalafmtPlugin

name := "receipts-rest-service"
organization := "rocks.leonti"
version := "1.0"
scalaVersion := "2.13.1"

addCompilerPlugin(scalafixSemanticdb)
addCommandAlias("fix", "; all compile:scalafix test:scalafix ; scalafmtAll ; e2e:scalafmt ; it:scalafmt ; ")
addCommandAlias(
    "check",
    "; compile:scalafix --check ; test:scalafix --check ; it:scalafix ; e2e:scalafix ; scalafmtCheckAll ; e2e:scalafmtCheck ; it:scalafmtCheck ; "
)

scalacOptions := Seq("-unchecked",
                     "-deprecation",
                     "-feature",
                     "-language:higherKinds",
                     "-Xfatal-warnings",
                     "-Wdead-code",
                     "-Xlint:inaccessible",
                     "-Xlint:unused",
                     "-Wunused:imports",
                     "-Ywarn-unused",
                     "-encoding",
                     "utf8")

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)

val awsV       = "1.11.241"
val scalaTestV      = "3.0.8"
val visionApiV      = "v1-rev346-1.22.0"
val googleApiClient = "1.22.0"
val scalaLoggingV   = "3.9.2"
val logbackV        = "1.1.7"
val diffsonV        = "4.0.0"
val catsV           = "2.0.0"
val circeVersion = "0.12.2"
val fs2V = "2.0.1"
val http4sVersion = "0.21.0-M5"
val scanamoV = "1.0.0-M11"

val logging = Seq(
  "ch.qos.logback"             % "logback-classic"          % logbackV,
  "com.typesafe.scala-logging" %% "scala-logging"           % scalaLoggingV,
  "org.slf4j"                  % "slf4j-api"                % "1.7.12",
  "net.logstash.logback"       % "logstash-logback-encoder" % "4.8"
)

inConfig(IntegrationTest)(ScalafmtPlugin.scalafmtConfigSettings)
inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest))

lazy val End2EndTest = Configuration.of("End2EndTest", "e2e") extend Runtime
lazy val root = (project in file("."))
  .configs(IntegrationTest, End2EndTest)
  .settings(
    Defaults.itSettings,
    inConfig(End2EndTest)(testSettings),
    inConfig(End2EndTest)(BloopDefaults.configSettings),
    inConfig(End2EndTest)(ScalafmtPlugin.scalafmtConfigSettings),
    inConfig(End2EndTest)(scalafixConfigSettings(End2EndTest))
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
    "org.typelevel"        %% "cats-core"              % catsV,
    "org.gnieh"     %% "diffson-circe" % diffsonV,
    "com.auth0" % "java-jwt" % "3.4.0" excludeAll (
      ExclusionRule(organization = "com.fasterxml.jackson.core")
      ),
    "com.auth0" % "jwks-rsa" % "0.6.0",
    "org.scanamo" %% "scanamo" % scanamoV,
    "org.scanamo" %% "scanamo-cats-effect" % scanamoV,
    "org.scanamo" %% "scanamo-testkit" % scanamoV,
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
test in assembly := {}
assemblyMergeStrategy in assembly := {
  case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
