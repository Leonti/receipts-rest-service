package service

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Path, Paths}

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import scala.concurrent.Future

class Cache(file : File) extends GraphStage[FlowShape[ByteString, ByteString]] {

  val in = Inlet[ByteString]("Cache.in")
  val out = Outlet[ByteString]("Cache.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(attr: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      val partFile = new File(file.getAbsolutePath + ".part")
      lazy val cacheFile = new FileOutputStream(partFile)

      println("Caching file to " + partFile)

      setHandler(in, new InHandler {
        override def onPush(): Unit = {

          val chunk: ByteString = grab(in)
          cacheFile.write(chunk.toArray)
          push(out, chunk)
        }

        override def onUpstreamFinish(): Unit = {
          println("Upstream finish, closing file")
          complete(out)
          cacheFile.close()
          partFile.renameTo(file)
        }
      })
      setHandler(out, new OutHandler {
        override def onPull(): Unit = {

          if (isClosed(in)) {
            complete(out)
          } else {
            pull(in)
          }
        }
      })
    }
}

class FileCachingService {

  val tempFile : (String, String) => File = (userId, fileId) => {
    val tmpDir = System.getProperty("java.io.tmpdir")
    Files.createDirectories(Paths.get(tmpDir, userId))
    Paths.get(tmpDir, userId, fileId).toFile
  }

  val cacheFlow : (String, String) => Flow[ByteString, ByteString, NotUsed] = (userId, fileId) =>
    Flow.fromGraph(new Cache(tempFile(userId, fileId)))

  val get : (String, String) => Option[Source[ByteString, Future[IOResult]]] = (userId, fileId) => {
    val file = tempFile(userId, fileId)
    if (file.exists()) Some(FileIO.fromPath(file.toPath)) else None
  }

}
