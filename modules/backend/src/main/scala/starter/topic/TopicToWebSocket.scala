package starter.topic

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.stream.ActorMaterializer
import akka.stream.alpakka.googlecloud.pubsub.grpc.scaladsl.GooglePubSub
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.google.pubsub.v1.pubsub.AcknowledgeRequest
import com.google.pubsub.v1.pubsub.ReceivedMessage
import com.google.pubsub.v1.pubsub.StreamingPullRequest
import com.typesafe.config.ConfigFactory
import starter.boot.EventProcessingActor
import starter.boot.RootActor
import starter.boot.RootActor.broadcastMessage

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import starter.config.ConfigurationParser
import starter.config.GooglePubSubConfiguration
import starter.data.CustomerCreated
import starter.data.CustomerRenamed
import starter.data.Event
import starter.data.EventEnvelope

class TopicToWebSocket(pubsubConfig: GooglePubSubConfiguration) {
  implicit val system = ActorSystem()

  def writeToFile(data: ByteString, file: java.io.File) = {
    val target = new BufferedOutputStream(new FileOutputStream(file))
    try (data.writeTo(target))
    finally target.close
  }
  def readFile(): ByteString = {
    val is    = new FileInputStream("./toto")
    val cnt   = is.available
    val bytes = Array.ofDim[Byte](cnt)
    is.read(bytes)
    is.close()
    ByteString.copyFrom(bytes)
  }

  def parseCustomerRenamed(byteString: ByteString): CustomerRenamed = {
    val codedInputStream = CodedInputStream.newInstance(byteString.toByteArray)
    val tag1             = codedInputStream.readTag()
    println(s"tag1 $tag1")

    val newName = codedInputStream.readStringRequireUtf8()
    println(s"name $newName")
    val tag2 = codedInputStream.readTag()
    println(s"tag2 $tag2")

    val date = codedInputStream.readInt64()
    println(s"date $date")

    val tag3 = codedInputStream.readTag()
    println(s"tag3 $tag3")

    val userID = codedInputStream.readStringRequireUtf8()
    println(s"userID $userID")
    CustomerRenamed(
      newName = newName,
      date = date,
      byUserID = userID
    )
  }

  def parseCustomerCreated(byteString: ByteString): CustomerCreated = ???

  /*
    def start2(): Unit = {
      val bytes = readFile()
      println("bytes " + bytes)
      parseCustomerRenamed(bytes)
    }*/

  def start(): Unit = {
    val subscription = s"projects/${pubsubConfig.projectId}/subscriptions/${pubsubConfig.subscription}"

    val request = StreamingPullRequest()
      .withSubscription(subscription)
      .withStreamAckDeadlineSeconds(10)

    val subscriptionSource: Source[ReceivedMessage, Future[Cancellable]] =
      GooglePubSub.subscribe(request, pollInterval = 1.second)
    val ackSink: Sink[AcknowledgeRequest, Future[Done]] =
      GooglePubSub.acknowledge(parallelism = 1)

    val _ = subscriptionSource
      .map { message =>
        // do something fun
        val pubSubMessageOpt = message.message
        pubSubMessageOpt match {
          case Some(pubSubMessage) =>
            println(s"received a message: ${pubSubMessage}")
            if (pubSubMessage.attributes.keySet.contains("ce-subject")) {
              val messageType = pubSubMessage.attributes("ce-type")
              val id          = pubSubMessage.attributes("ce-subject")
              //val messageId          = pubSubMessage.messageId
//              val id      = "AAA"
              val content = pubSubMessage.data
              println(s"id ${pubSubMessage.attributes.keySet}")
              println(s"id ${pubSubMessage.attributes.keySet.contains("ce-subject")}")
              pubSubMessage.attributes.keySet.foreach(k => println(s"<${pubSubMessage.attributes(k)}>"))
              //println(idd)
              val keys = pubSubMessage.attributes.keys.toList
              //val values = pubSubMessage.attributes.values.toSeq
              val index = keys.indexOf("ce-subject")
              println(s"index $index")
              //val value = values(index)
              //println(s"value $value")

              //writeToFile(content, new java.io.File("./toto"))
              val event: Event = messageType match {
                case "com.example.customer.domain.CustomerRenamed" =>
                  val customerRenamed = parseCustomerRenamed(content)
                  println(s"received CustomerRenamed " + customerRenamed)
                  customerRenamed
                case "com.example.customer.domain.CustomerCreated" =>
                  println(s"received CustomerCreated")
                  val customerCreated = parseCustomerCreated(content)
                  customerCreated
              }
              val eventEnvelope = EventEnvelope(event, id)
              val json          = eventEnvelope.asJson.noSpaces

              println("json " + json)
              RootActor.broadcastMessage(EventProcessingActor.Command.PushText(json))

            }

          case None =>
            println("Receive empty message")
        }

        message.ackId
      }
      .groupedWithin(10, 1.second)
      .map(ids =>
        AcknowledgeRequest()
          .withSubscription(
            subscription
          )
          .withAckIds(ids)
      )
      .runWith(ackSink)
  }

}
