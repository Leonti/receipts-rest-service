package backup

import java.io.OutputStream

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2.{Chunk, Pipe, Stream, io}
import java.util.zip.{ZipEntry, ZipOutputStream}
import fs2.concurrent.Queue

import scala.concurrent.{ExecutionContext, SyncVar}

// https://github.com/slamdata/fs2-gzip/blob/master/core/src/main/scala/fs2/gzip/package.scala
// https://github.com/scalavision/fs2-helper/blob/master/src/main/scala/fs2helper/zip.scala
// https://github.com/eikek/sharry/blob/2f1dbfeae3c73bf2623f65c3591d0b3e0691d4e5/modules/common/src/main/scala/sharry/common/zip.scala

object Fs2Zip {

  def writeEntry[F[_]](zos: ZipOutputStream)
                      (implicit F: Concurrent[F], blockingEc: ExecutionContext, contextShift: ContextShift[F]):
  Pipe[F, (String, Stream[F, Byte]), Unit] =
    _.flatMap {
      case (name, data) =>
        val createEntry = Stream.eval(F.delay {
          zos.putNextEntry(new ZipEntry(name))
        })
        val writeEntry = data.through(
          io.writeOutputStream(
            F.delay(zos.asInstanceOf[OutputStream]),
            blockingEc,
            closeAfterUse = false))
        val closeEntry = Stream.eval(F.delay(zos.closeEntry()))
        createEntry ++ writeEntry ++ closeEntry
    }

  def zipP1[F[_]](implicit F: ConcurrentEffect[F], blockingEc: ExecutionContext, contextShift: ContextShift[F]):
  Pipe[F, (String, Stream[F, Byte]), Byte] = entries => {

    Stream.eval(Queue.unbounded[F, Option[Vector[Byte]]]).flatMap { q =>

      Stream.suspend {
        val os = new java.io.OutputStream {

          private def enqueueChunkSync(a: Option[Vector[Byte]]) = {
            val done = new SyncVar[Either[Throwable, Unit]]
            q.enqueue1(a).start.flatMap(_.join).runAsync(e => IO(done.put(e))).unsafeRunSync
            done.get.fold(throw _, identity)
          }
          @scala.annotation.tailrec
          private def addChunk(newChunk: Vector[Byte]): Unit = {
            val newChunkSize = newChunk.size
            val bufferedChunkSize = bufferedChunk.size
            val spaceLeftInTheBuffer = newChunkSize - bufferedChunkSize
            if (newChunkSize > spaceLeftInTheBuffer) {
              // Not enough space in the buffer to contain whole new chunk.
              // Recursively slice and enqueue chunk
              // in order to preserve chunk size.
              val fullBuffer = bufferedChunk ++ newChunk.take(spaceLeftInTheBuffer)
              enqueueChunkSync(Some(fullBuffer))
              bufferedChunk = Vector.empty
              addChunk(newChunk.drop(spaceLeftInTheBuffer))
            } else {
              // There is enough space in the buffer for whole new chunk
              bufferedChunk = bufferedChunk ++ newChunk
            }
          }
          private var bufferedChunk: Vector[Byte] = Vector.empty
          override def close(): Unit = {
            // flush remaining chunk
            enqueueChunkSync(Some(bufferedChunk))
            bufferedChunk = Vector.empty
            // terminate the queue
            enqueueChunkSync(None)
          }
          override def write(bytes: Array[Byte]): Unit =
            addChunk(Vector(bytes: _*))
          override def write(bytes: Array[Byte], off: Int, len: Int): Unit =
            addChunk(Chunk.bytes(bytes, off, len).toVector)
          override def write(b: Int): Unit =
            addChunk(Vector(b.toByte))
        }
        val write: Stream[F, Unit] = Stream.bracket(F.delay(new ZipOutputStream(os)))((zos: ZipOutputStream) => F.delay(zos.close()))
          .flatMap((zos: ZipOutputStream) => entries.through(writeEntry(zos)))
        val read: Stream[F, Byte] = q
          .dequeue
          .unNoneTerminate // `None` in the stream terminates it
          .flatMap(Stream.emits(_))

        read.concurrently(write)
      }
    }
  }

  def zip[F[_]](entries: Stream[F, (String, Stream[F, Byte])])
               (implicit F: ConcurrentEffect[F], ec: ExecutionContext, contextShift: ContextShift[F]): Stream[F, Byte] =
    entries.through(zipP1)
}
