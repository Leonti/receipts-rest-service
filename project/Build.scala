import java.io.IOException
import java.net.URL
import sbt._
import Keys._
import scala.util.{Failure, Success, Try}



//rather generic project build definition - enabling integration tests
object MyBuildBuild extends Build {
  lazy val root = Project(id = "root",
    base = file(".")).
    configs(IntegrationTest).
    settings(Defaults.itSettings : _*).
    settings(testOptions in IntegrationTest += Tests.Setup({_ => AppRunnerRemoteControl.start()})).
    settings(testOptions in IntegrationTest += Tests.Cleanup({_ => AppRunnerRemoteControl.stop()})).
    settings(parallelExecution in IntegrationTest := false)
}

//the core part of solution - shared object
object AppRunnerRemoteControl {
  //receive class path from main build definition
  def setClassPath(cp: Seq[File]): Unit = {
    this.cp = cp
  }
  //in order to have remote control logs in same style as the build logs
  def setLog(log: Logger): Unit = {
    this.log = Option(log)
  }

  def start(): Unit = {
    log.foreach(_.info("starting application ..."))
    val options = ForkOptions(outputStrategy = Some(StdoutOutput))
    //build classpath string
    val cpStr = cp.map(_.getAbsolutePath).mkString(":")
    val userHome = System.getProperty("user.home")
    val property =  s"-Dconfig.file=${userHome}/.receipts-rest-service/service.conf"
    val arguments: Seq[String] = List("-classpath", cpStr, property)
    //Here goes the name of the class which would be launched
    val mainClass: String = "ReceiptRestService"
    //Launch it. Pay attention that class name comes last in the list of arguments
    proc = Option(Fork.java.fork(options, arguments :+ mainClass))

    //make sure application really started or failed before proceed to the tests
    waitForStart().recover({case e =>
      stop()
      throw e
    }).get
  }

  def stop(): Unit = {
    log.foreach(_.info(s"stopping application $proc ..."))
    //kill application
    proc.foreach(_.destroy())
    proc = None
  }

  private def waitForStart(): Try[_] = {
    val maxAttempts = 15
    val u = new URL("http://localhost:9000")
    val c = u.openConnection()
    val result = (1 to maxAttempts).toStream map {i =>
      log.foreach(_.info(s"connection attempt $i of $maxAttempts"))
      Try {c.connect()}} find {
      case Success(_) => true
      case Failure(e: IOException) => Thread.sleep(1000); false
      case Failure(_) => false
    }
    if(result.isEmpty)
      Failure(new RuntimeException(s"Failed to connect to application after $maxAttempts attempts"))
    else
      Success(None)
  }

  var log: Option[Logger] = None
  var cp: Seq[File] = Nil
  var proc: Option[Process] = None
}