package starter

import org.scalajs.dom
import org.scalajs.dom._

import io.circe.generic.auto._
import io.circe.parser.decode

import starter.data.EventEnvelope
// google: scala.js websocket send java.nio.ByteBuffer
// ->
// Could be the solution:
// https://github.com/kiritsuku/amora/blob/master/web-ui/src/main/scala/amora/frontend/webui/Connection.scala

object WebSocketClient {
  val url                       = "ws://localhost:8080/ws"
  var socketOpt: Option[Socket] = None

  def setSocket() = {
    socketOpt = Some(
      Socket(url)((event: MessageEvent) =>
        event.data match {
          case text: String =>
            println("Socket received text " + text)
            val eventEither = decode[EventEnvelope](text)
            eventEither match {
              case Right(eventEnvelope) =>
                println("Event received " + eventEnvelope)
                DataModel.eventReceived(eventEnvelope)
              case Left(error) =>
                println("Decoding error " + error)
            }

          case blob: Blob =>
            println("Socket received blob " + blob)
          case _ => println("Error on receive, should be a blob.")
        }
      )
    )

  }

  def send(msg: String): Unit = {
    if (socketOpt.isEmpty) {
      setSocket()
    }
    println("Socket send " + msg)
    socketOpt.get.send(msg)
  }

  case class Socket(url: String)(onMessage: (MessageEvent) => _) {
    println("  SOCKETE SOCKETE SOCKETE " + url)

    private val socket: WebSocket = new dom.WebSocket(url = url)

    def send(msg: String): Unit = {
      socket.send(msg)
    }

    socket.onopen = (e: Event) => {}
    socket.onclose = (e: CloseEvent) => {
      println(s"Socket closed. Reason: ${e.reason} (${e.code})")
      setSocket()

    }
    socket.onerror = (e: Event) => {
      println(s"Socket error! ${e}")
    }
    socket.onmessage = onMessage
  }

}
