package starter.inspectcustomer

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import starter.DataModel
import starter.components.Link
import starter.pages.AllCustomersPage
import starter.pages.InspectCustomerPage
import org.scalajs.dom.html
import starter.DataModel.$customers
import starter.DataModel.CustomerData
import starter.DataModel.diffBus
import starter.DataModel.sendCustomerRenamed
import starter.data.CustomerCreated
import starter.data.CustomerRenamed
import starter.data.CustomerSnapshot
import starter.data.Event
import starter.data.EventEnvelope

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object InspectCustomer extends Owner {
  val nameVar = Var(initial = "")

  def renderField(name: String, $value: Signal[String]): HtmlElement = {
    div(
      label(cls := "block text-sm font-medium text-gray-700", name),
      span(
        cls := "mt-1 focus:ring-indigo-500 focus:border-indigo-500 block w-full shadow-sm sm:text-sm border-gray-300 rounded-md",
        child.text <-- $value
      )
    )
  }
  def renderField2(name: String, value: String): HtmlElement = {
    div(
      label(cls := "block text-sm font-medium text-gray-700", name),
      span(
        cls := "mt-1 focus:ring-indigo-500 focus:border-indigo-500 block w-full shadow-sm sm:text-sm border-gray-300 rounded-md",
        value
      )
    )
  }

  val $errorVar = Var(Option.empty[String])
  var fetched   = Set.empty[String]

  def render($trigram: Signal[InspectCustomerPage]): HtmlElement = {
    val $g = $trigram.map(trigram => {
      if (fetched.contains(trigram.trigram)) {
        println("Customers details already fetched")
        renderPage($trigram)
      } else {
        div(
          inContext { thisNode =>
            val $s = DataModel
              .fetchCustomerDetails(trigram.trigram).map(
                _ match {
                  case Right(customerDetails) =>
                    println("Envelopes " + customerDetails)
                    val stream = EventStream.fromSeq(customerDetails)
                    stream.addObserver(diffBus.toObserver)(this)
                    fetched += trigram.trigram
                    renderPage($trigram)
                  case Left(errorMessage) =>
                    renderError(errorMessage)
                }
              )
            div(
              child <-- $s
            )
          }
        )
      }
    })
    div(
      child <-- $g
    )
  }

  def renderPage($trigram: Signal[InspectCustomerPage]): HtmlElement = {
    val $customerOpt = $customers.withCurrentValueOf($trigram)
      .map(tuple => {
        tuple._1.find(c => c.trigram.equals(tuple._2.trigram))
      })

    div(
      inContext { thisNode =>
        val $s = $customerOpt.map {
          case Some(customer) =>
            renderCustomer(customer)
          case None =>
            div("Customer not found")
        }
        child <-- $s
      }
    )
  }

  def renderCustomer(customer: CustomerData) = {
    div(
      cls := "flex justify-center sm:max-w-xl md:max-w-full md:px-24 lg:px-8 lg:py-10 w-full bg-top bg-cover mt-0 mr-auto mb-0 ml-auto pt-16 pr-4 pb-16 pl-4 component-selected",
      div(
        cls := "lg:grid-cols-2",
        h1(cls := "text-4xl font-normal", "Inspect Customer"),
        div(
          cls := "a-wrapper",
          Link(
            AllCustomersPage,
            cls := "mt-1 block px-3 py-2 rounded-md text-base font-medium text-gray-300 hover:text-white hover:bg-gray-700 focus:outline-none focus:text-white focus:bg-gray-700",
            "All Customers"
            //onClick --> (_ => { DataModel.removeStreamObservers() })
          ),
          div(
            cls := "px-4 py-5 bg-white sm:p-6",
            div(
              cls := "grid grid-cols-6 gap-6",
              div(
                label(cls := "block text-sm font-medium text-gray-700", "Name"),
                input(
                  cls := "mt-1 focus:ring-indigo-500 focus:border-indigo-500 block w-full shadow-sm sm:text-sm border-gray-300 rounded-md",
                  placeholder := customer.customer.name,
                  inContext { thisNode => onInput.map(_ => thisNode.ref.value) --> nameVar }
                ),
                button(
                  cls := "h-9 w-24 rounded-lg bg-blue-900 border-blue-900 " +
                    "mt-0 mr-0 mb-0 ml-0 pt-0 pr-0 pb-0 pl-0 text-center text-base font-normal flex items-center justify-center text-white border-blue-700",
                  styleAttr := "font-family: Arial;",
                  "Rename",
                  inContext(chose => {
                    val truc = chose
                      .events(onClick)
                      .sample(
                        nameVar.signal
                      ).flatMap(t => sendCustomerRenamed(t, customer.trigram))
                    truc --> $errorVar
                  })
                ),
                div(
                  child.text <-- $errorVar.signal.map(opt =>
                    opt match {
                      case Some(error) => error
                      case None        => ""
                    }
                  )
                )
              )
            ),
            renderField2("Trigram", customer.trigram),
            renderField2("Customer ID", customer.customer.customerID.toString),
            renderField2("Customer Type", customer.customer.customerType),
            renderField2("Dynamics Account ID", customer.customer.dynamicsAccountID),
            renderField2("Head Country", customer.customer.headCountry),
            renderField2("Region", customer.customer.region)
          ),
          renderHistory(customer)
        )
      )
    )
  }

  def renderError(errorMessage: String): ReactiveHtmlElement[html.Div] = {
    div(cls := "text-purple-600", s"error $errorMessage")
  }

  case class HistoryEvent(action: String, date: String, userID: String, description: String)

  def eventEnvelopeToHistoryEvent(envelope: EventEnvelope) = {
    eventToHistoryEvent(envelope.event)
  }
  def eventToHistoryEvent(event: Event) = {
    event match {
      case created: CustomerCreated =>
        HistoryEvent("Customer Created", formattedDate(created.date), created.byUserID, "")
      case renamed: CustomerRenamed =>
        HistoryEvent("Customer Renamed", formattedDate(renamed.date), renamed.byUserID, s"Renamed to ${renamed.newName}")
      case snapshot: CustomerSnapshot =>
        HistoryEvent("Customer Snapshot", formattedDate(0L), "philippe.pulvin@nagra.com", snapshot.toString)
    }
  }

  val format1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  def formattedDate(time: Long): String = {
    val instant = Instant.ofEpochMilli(time)
    val date    = LocalDateTime.ofInstant(instant, ZoneOffset.ofHours(1))
    date.format(format1)
  }

  def renderHistory(customer: DataModel.CustomerData): ReactiveHtmlElement[html.Div] = {
    val historyEvents = customer.customer.events.map(eventToHistoryEvent(_))

    div(
      table(
        cls := "border-collapse",
        styleAttr := "border: 1px solid #dee2e6; width: 100%; margin-bottom: 1rem; color: #212529; border-spacing: 2px;",
        thead(
          cls := "",
          styleAttr := "background-color: rgba(0, 0, 0, 0.075); border-spacing: 2px;",
          tr(
            th(styleAttr := "border-bottom-width: 2px; padding: 12px; display: table-cell; border: 1px solid #dee2e6;", "Action"),
            th(styleAttr := "border-bottom-width: 2px; padding: 12px; display: table-cell; border: 1px solid #dee2e6;", "Date"),
            th(styleAttr := "border-bottom-width: 2px; padding: 12px; display: table-cell; border: 1px solid #dee2e6;", "User"),
            th(styleAttr := "border-bottom-width: 2px; padding: 12px; display: table-cell; border: 1px solid #dee2e6;", "Description")
          )
        ),
        tbody(
          historyEvents.map(event => renderEvent(event))
        )
      )
    )

  }

  private def renderEvent(event: HistoryEvent) = {
    tr(
      td(event.action),
      td(event.date),
      td(event.userID),
      td(event.description)
    )
  }

}
