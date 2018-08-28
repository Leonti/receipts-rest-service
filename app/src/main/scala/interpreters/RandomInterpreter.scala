package interpreters

import java.io.File

import algebras.RandomAlg
import cats.effect.IO

// TODO - no reason to use Future here
class RandomInterpreterTagless extends RandomAlg[IO] {
  override def generateGuid(): IO[String] = IO(java.util.UUID.randomUUID.toString)
  override def getTime(): IO[Long]        = IO(System.currentTimeMillis())
  override def tmpFile(): IO[File]        = IO(File.createTempFile("receipt", "file"))
}
