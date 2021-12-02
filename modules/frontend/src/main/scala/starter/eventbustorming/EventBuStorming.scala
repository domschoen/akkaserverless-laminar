package starter.eventbustorming

import com.raquo.airstream.web.AjaxEventStream
import com.raquo.airstream.web.AjaxEventStream.AjaxStreamError
import com.raquo.laminar.CollectionCommand
import com.raquo.laminar.api.L._
import org.scalajs.dom
import com.softwaremill.quicklens._
import io.circe.derivation.annotations.JsonCodec
import io.circe.syntax.EncoderOps
import starter.App.userVar
import starter.WebSocketClient
import starter.config.Variables
import starter.data.CustomerCreated
import starter.data.CustomerRenamed
import starter.data.CustomerSnapshot
import starter.data.Event
import starter.data.EventEnvelope
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import starter.DataModel.$customers
import starter.DataModel.diffBus
import starter.DataModel.fetchCustomers
import starter.DataModel.sendCustomerRenamed
import starter.DataModel.sendCustomerRenamedUI

object EventBuStorming extends Owner {

  def Counter(): HtmlElement = {

    val nameVar = Var(initial = "")

    div(
      p(
        "New name for AAA: ",
        input(
          onMountFocus,
          placeholder := "Enter your name here",
          inContext { thisNode => onInput.map(_ => thisNode.ref.value) --> nameVar }
        )
      ),
      p("Current customer: ", child.text <-- $customers.map(c => { c.toString })),
      p(
        button("Rename", inContext(_.events(onClick).sample(nameVar.signal.combineWith(Var("AAA"))) --> sendCustomerRenamedUI()))
      )
    )
  }

  def render: HtmlElement = {
    div(
      h1("Let's do Event Sourcing with Event Bus"),
      Counter(),
      renderAjax()
    )
  }

  def renderAjax() = {
    import com.raquo.airstream.web.AjaxEventStream
    import com.raquo.airstream.web.AjaxEventStream.AjaxStreamError
    import com.raquo.laminar.api.L._
    import org.scalajs.dom

    // Example based on plain JS version: http://plnkr.co/edit/ycQbBr0vr7ceUP2p6PHy?preview

    case class AjaxOption(name: String, url: String)

    val options = List(
      AjaxOption("Valid Ajax request", "https://api.zippopotam.us/us/90210"),
      AjaxOption("Download 100MB file (gives you time to abort)", "https://cachefly.cachefly.net/100mb.test"),
      AjaxOption("URL that will fail due to invalid domain", "https://api.zippopotam.uxx/us/90210"),
      AjaxOption("URL that will fail due to CORS restriction", "https://unsplash.com/photos/KDYcgCEoFcY/download?force=true")
    )
    val selectedOptionVar = Var(options.head)
    val pendingRequestVar = Var[Option[dom.XMLHttpRequest]](None)
    val eventsVar         = Var(List.empty[String])

    div(
      h1("Ajax Tester"),
      options.map { option =>
        div(
          input(
            idAttr(option.name),
            typ("radio"),
            name("ajaxOption"),
            checked <-- selectedOptionVar.signal.map(_ == option),
            onChange.mapTo(option) --> selectedOptionVar
          ),
          label(forId(option.name), " " + option.name)
        )
      },
      br(),
      div(
        button(
          "Send",
          inContext { thisNode =>
            val $click = thisNode.events(onClick).sample(selectedOptionVar.signal)
            val $response = $click.flatMap { opt =>
              AjaxEventStream
                .get(
                  url = opt.url,
                  // These observers are optional, we're just using them for demo
                  requestObserver = pendingRequestVar.someWriter,
                  progressObserver = eventsVar.updater { (evs, p) =>
                    val ev = p._2
                    evs :+ s"Progress: ${ev.loaded} / ${ev.total} (lengthComputable = ${ev.lengthComputable})"
                  },
                  readyStateChangeObserver = eventsVar.updater { (evs, req) =>
                    evs :+ s"Ready state: ${req.readyState}"
                  }
                )
                .map("Response: " + _.responseText)
                .recover { case err: AjaxStreamError => Some(err.getMessage) }
            }

            List(
              $click.map(opt => List(s"Starting: GET ${opt.url}")) --> eventsVar,
              $response --> eventsVar.updater[String](_ :+ _)
            )
          }
        ),
        " ",
        button(
          "Abort",
          onClick --> (_ => pendingRequestVar.now().foreach(_.abort()))
        )
      ),
      div(
        h2("Events:"),
        div(children <-- eventsVar.signal.map(_.map(div(_))))
      )
    )
  }
}
