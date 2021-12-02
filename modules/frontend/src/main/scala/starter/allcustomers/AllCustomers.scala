package starter.allcustomers

import com.raquo.airstream.web.AjaxEventStream
import com.raquo.airstream.web.AjaxEventStream.AjaxStreamError
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.circe.derivation.annotations.JsonCodec
import org.scalajs.dom.ext.Ajax
import starter.components.Link
import starter.pages.AddCustomerPage
import starter.pages.InspectCustomerPage
import io.circe.parser
import starter.DataModel
import starter.Routes
import starter.config.Variables
import com.softwaremill.quicklens._
import org.scalajs.dom.html
import starter.DataModel.$customers
import starter.DataModel.CustomerData
import starter.DataModel.diffBus

object AllCustomers extends Owner {

  case class SearchBox private (node: Element, signal: Signal[String])
  object SearchBox {
    def create = {
      val node = input(
        `type` := "text",
        idAttr := "search-filter"
      )

      val stream =
        node
          .events(onInput).mapTo {
            println("typed " + node.ref.value)
            node.ref.value
          }.startWith("")

      new SearchBox(node, stream)
    }
  }

  val fetchError = Var(Option.empty[String])
  var fetched    = false

  def render() = {
    if (fetched) {
      println("Customers Already fetched")
      renderPage()
    } else {
      div(
        inContext { thisNode =>
          println("Fetching customers")
          val $s = DataModel
            .fetchCustomers().map(_ match {
              case Right(fetchedData) =>
                val stream = DataModel.customerCustomerStreamToEventStream(EventStream.fromSeq(fetchedData))
                stream.addObserver(diffBus.toObserver)(this)
                fetched = true
                renderPage()

              case Left(errorMessage) =>
                renderError(errorMessage)
            })
          div(
            child <-- $s
          )
        }
      )
    }
  }

  def renderError(errorMessage: String): ReactiveHtmlElement[html.Div] = {
    div(cls := "text-purple-600", s"error $errorMessage")
  }

  def sortCustomers(cds: List[CustomerData]): List[CustomerData] = {
    cds.sortWith((a, b) => a.customer.name.toLowerCase() < b.customer.name.toLowerCase())
  }

  def renderPage(): ReactiveHtmlElement[html.Div] = {
    val searchBox = SearchBox.create
    val $filteredCustomers = searchBox.signal
      .combineWith($customers)
      .map(tuple => {
        println("Current value " + tuple)
        val customers = tuple._2
        val criteria  = tuple._1
        sortCustomers(customers.filter(c => criteria.isEmpty || c.customer.name.toLowerCase().startsWith(criteria.toLowerCase())))
      })
    div(
      cls := "sm:max-w-xl md:max-w-full md:px-24 lg:px-8 lg:py-10 w-full bg-top bg-cover mt-0 mr-auto mb-0 ml-auto pt-16 pr-4 pb-16 pl-4 component-selected",
      h1(cls := "text-4xl font-normal", "Customers "),
      div(
        Link(
          AddCustomerPage,
          cls := "mt-1 block px-3 py-2 rounded-md text-base font-medium text-gray-300 hover:text-white hover:bg-gray-700 focus:outline-none focus:text-white focus:bg-gray-700",
          "Add New Customer"
        ),
        div("Search: ", searchBox.node),
        div(child.text <-- starter.DataModel.$customers.combineWith($filteredCustomers).map(tuple => {
          s"${tuple._2.size.toString} / ${tuple._1.size.toString} records"
        })),
        table(
          cls := "border-collapse",
          styleAttr := "border: 1px solid #dee2e6; width: 100%; margin-bottom: 1rem; color: #212529; border-spacing: 2px;",
          thead(
            cls := "",
            styleAttr := "background-color: rgba(0, 0, 0, 0.075); border-spacing: 2px;",
            tr(
              th(styleAttr := "border-bottom-width: 2px; padding: 12px; display: table-cell; border: 1px solid #dee2e6;", ""),
              th(styleAttr := "border-bottom-width: 2px; padding: 12px; display: table-cell; border: 1px solid #dee2e6;", "Name"),
              th(styleAttr := "border-bottom-width: 2px; padding: 12px; display: table-cell; border: 1px solid #dee2e6;", "Trigram"),
              th(styleAttr := "border-bottom-width: 2px; padding: 12px; display: table-cell; border: 1px solid #dee2e6;", "Open in IST"),
              th(styleAttr := "border-bottom-width: 2px; padding: 12px; display: table-cell; border: 1px solid #dee2e6;", "Customer Type"),
              th(styleAttr := "border-bottom-width: 2px; padding: 12px; display: table-cell; border: 1px solid #dee2e6;", "Dynamics Account ID"),
              th(styleAttr := "border-bottom-width: 2px; padding: 12px; display: table-cell; border: 1px solid #dee2e6;", "Head Country"),
              th(styleAttr := "border-bottom-width: 2px; padding: 12px; display: table-cell; border: 1px solid #dee2e6;", "Region")
            )
          ),
          tbody(
            children <-- $filteredCustomers.split(_.trigram)(renderCustomer)
          )
        )
      )
    )
  }

  def inspect = Observer[String] { trigram =>
    Routes.pushState(InspectCustomerPage(trigram))
  }

  private def renderCustomer(trigram: String, initialCustomer: CustomerData, $item: Signal[CustomerData]): HtmlElement = {
    tr(
      td(
        svg.svg(
          svg.xmlns := "http://www.w3.org/2000/svg",
          svg.cls := "h-5 w-5",
          svg.fill := "currentColor",
          svg.viewBox := "0 0 20 20",
          svg.stroke := "currentColor",
          svg.path(
            svg.d := "M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z"
          ),
          onClick.mapTo(trigram) --> inspect
        )
      ),
      td(child.text <-- $item.map(_.customer.name)),
      td(child.text <-- $item.map(_.trigram)),
      td(
        a(
          cls := "font-light",
          styleAttr := "color: #007bff",
          target := "_blank",
          href <-- $item.map("https://ist.hq.k.grp/cgi-bin/WebObjects/ist.woa/wa/inspectRecord?entityName=Customer&id=" + _.customer.customerID),
          "ist"
        )
      ),
      td(child.text <-- $item.map(_.customer.customerType)),
      td(child.text <-- $item.map(_.customer.dynamicsAccountID)),
      td(child.text <-- $item.map(_.customer.headCountry)),
      td(child.text <-- $item.map(_.customer.region))
    )
  }

}
