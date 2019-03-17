package queue
import algebras.QueueAlg
import cats.effect.IO
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{ReceiveMessageRequest, SendMessageRequest}
import collection.JavaConverters._

class QueueSqs(client: AmazonSQS, queueName: String) extends QueueAlg[IO] {

  private val queueUrl = client.getQueueUrl(queueName).getQueueUrl

  override def submit(queueJob: QueueJob): IO[Unit] = IO {
    val sendMsgRequest = new SendMessageRequest()
      .withQueueUrl(queueUrl)
      .withMessageBody(QueueJob.asString(queueJob))
    client.sendMessage(sendMsgRequest)
  }
  override def reserve(): IO[Option[ReservedJob]] = IO {
    val receiveMessageRequest = new ReceiveMessageRequest()
      .withQueueUrl(queueUrl)
        .withMaxNumberOfMessages(1)
    client.receiveMessage(receiveMessageRequest).getMessages.asScala.toList
      .headOption
      .map(m => ReservedJob(m.getReceiptHandle, QueueJob.fromString(m.getBody)))
  }
  override def delete(id: String): IO[Unit]              = IO {
    client.deleteMessage(queueUrl, id)
  }
  override def release(id: String): IO[Unit]             = IO.pure(())
  override def bury(id: String): IO[Unit]                = IO.pure(())
}
