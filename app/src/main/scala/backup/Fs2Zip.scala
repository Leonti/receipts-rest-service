package backup

import java.io.OutputStream

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2.{Chunk, Pipe, Stream, io}
import java.util.zip.{ZipEntry, ZipOutputStream}

import fs2.concurrent.Queue
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import scala.concurrent.ExecutionContext

// https://github.com/slamdata/fs2-gzip/blob/master/core/src/main/scala/fs2/gzip/package.scala
// https://github.com/scalavision/fs2-helper/blob/master/src/main/scala/fs2helper/zip.scala
// https://github.com/eikek/sharry/blob/2f1dbfeae3c73bf2623f65c3591d0b3e0691d4e5/modules/common/src/main/scala/sharry/common/zip.scala

object Fs2Zip {

  private val CHUNK_SIZE = 8192

  private def writeEntry[F[_]](
      zos: ZipOutputStream
  )(implicit F: Concurrent[F], blockingEc: ExecutionContext, contextShift: ContextShift[F]): Pipe[F, (String, Stream[F, Byte]), Unit] =
    _.flatMap {
      case (name, data) =>
        val createEntry = Stream.eval(F.delay {
          zos.putNextEntry(new ZipEntry(name))
        })
        val writeEntry = data.through(
          io.writeOutputStream(F.delay(zos.asInstanceOf[OutputStream]), Blocker.liftExecutionContext(blockingEc), closeAfterUse = false)
        )
        val closeEntry = Stream.eval(F.delay(zos.closeEntry()))
        createEntry ++ writeEntry ++ closeEntry
    }

  private def zipP1[F[_]](
      implicit F: ConcurrentEffect[F],
      blockingEc: ExecutionContext,
      contextShift: ContextShift[F]
  ): Pipe[F, (String, Stream[F, Byte]), Byte] = entries => {

    Stream.eval(Queue.unbounded[F, Option[Chunk[Byte]]]).flatMap { q =>
      Stream.suspend {
        val os = new java.io.OutputStream {

          private def enqueueChunkSync(a: Option[Chunk[Byte]]) = {
            val done = new LinkedBlockingQueue[Either[Throwable, Unit]](1)
            val enq  = q.enqueue1(a).start.flatMap(_.join).runAsync(e => IO(done.put(e))).to[F]
            (contextShift.shift *> enq).toIO.unsafeRunSync
            done.poll(10, TimeUnit.SECONDS).fold(throw _, identity)
          }
          @scala.annotation.tailrec
          private def addChunk(c: Chunk[Byte]): Unit = {
            val free = CHUNK_SIZE - bufferedChunk.size
            if (c.size > free) {
              enqueueChunkSync(Some(Chunk.vector(bufferedChunk.toVector ++ c.take(free).toVector)))
              bufferedChunk = Chunk.empty
              addChunk(c.drop(free))
            } else {
              bufferedChunk = Chunk.vector(bufferedChunk.toVector ++ c.toVector)
            }
          }

          private var bufferedChunk: Chunk[Byte] = Chunk.empty

          override def close(): Unit = {
            // flush remaining chunk
            enqueueChunkSync(Some(bufferedChunk))
            bufferedChunk = Chunk.empty
            // terminate the queue
            enqueueChunkSync(None)
          }
          override def write(bytes: Array[Byte]): Unit =
            addChunk(Chunk.bytes(bytes))
          override def write(bytes: Array[Byte], off: Int, len: Int): Unit =
            addChunk(Chunk.bytes(bytes, off, len))
          override def write(b: Int): Unit =
            addChunk(Chunk.singleton(b.toByte))
        }

        val write: Stream[F, Unit] = Stream
          .bracket(F.delay(new ZipOutputStream(os)))((zos: ZipOutputStream) => F.delay(zos.close()))
          .flatMap((zos: ZipOutputStream) => entries.through(writeEntry(zos)))

        val read = q.dequeue.unNoneTerminate
          .flatMap(Stream.chunk(_))

        read.concurrently(write)
      }
    }
  }

  def zip[F[_]: ConcurrentEffect: ContextShift](
      entries: Stream[F, (String, Stream[F, Byte])]
  )(implicit ec: ExecutionContext): Stream[F, Byte] =
    entries.through(zipP1)
}
