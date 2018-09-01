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

val akkaV           = "2.4.20"
val akkaHttpV       = "10.0.11"
val amazonS3V       = "1.11.241"
val scalaTestV      = "3.0.4"
val reactiveMongoV  = "0.12.6"
val visionApiV      = "v1-rev346-1.22.0"
val googleApiClient = "1.22.0"
val scalaLoggingV   = "3.5.0"
val logbackV        = "1.1.7"
val diffsonV        = "3.0.0"
val catsV           = "1.2.0"
val circeVersion = "0.9.3"
val finchV = "0.23.0"
val fs2V = "1.0.0-M1"
val http4sVersion = "1.0.0-SNAPSHOT"

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
    "com.typesafe.akka"     %% "akka-http-testkit"         % akkaHttpV,
    "de.heikoseeberger" %% "akka-http-circe" % "1.21.0",
    "com.github.finagle" %% "finch-core" % finchV,
    "com.github.finagle" %% "finch-circe" % finchV,
    "com.amazonaws"         % "aws-java-sdk-s3"            % amazonS3V,
    "com.google.apis"       % "google-api-services-vision" % visionApiV excludeAll (
      ExclusionRule(organization="com.google.guava", name="guava-jdk5")
      ),
    "com.google.api-client" % "google-api-client"          % googleApiClient excludeAll (
      ExclusionRule(organization="com.google.guava", name="guava-jdk5")
      ),
    "org.reactivemongo"     %% "reactivemongo"             % reactiveMongoV excludeAll (
      ExclusionRule(organization = "com.typesafe.akka")
    ),
    "com.drewnoakes"       % "metadata-extractor" % "2.9.0",
    "org.typelevel"        %% "cats-core"              % catsV,
    "org.gnieh"     %% "diffson-circe" % diffsonV,
    "com.auth0" % "java-jwt" % "3.4.0" excludeAll (
      ExclusionRule(organization = "com.fasterxml.jackson.core")
      ),
    "com.auth0" % "jwks-rsa" % "0.6.0",
    "co.fs2" %% "fs2-core" % fs2V,
    "co.fs2" %% "fs2-io" % fs2V,
    "org.scalatest" %% "scalatest"          % scalaTestV % "it,test"
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
