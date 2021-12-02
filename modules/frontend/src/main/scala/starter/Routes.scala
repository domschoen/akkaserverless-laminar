package starter

import com.raquo.laminar.api.L._
import com.raquo.waypoint._
import org.scalajs.dom
import io.circe.syntax._
import io.circe.parser._
import starter.DataModel.CustomerData
import starter.addcustomer.AddCustomer
import starter.allcustomers.AllCustomers
import starter.eventbustorming.EventBuStorming
import starter.inspectcustomer.InspectCustomer
import starter.pages._

object Routes {

  private val testRESTCallRoute    = Route.static(TestRESTCallPage, root / "debug" / endOfSegments)
  private val allCustomersRoute    = Route.static(AllCustomersPage, root / "customers" / endOfSegments)
  private val addCustomerRoute     = Route.static(AddCustomerPage, root / "addCustomer" / endOfSegments)
  private val eventBuStormingRoute = Route.static(EventBuStormingPage, root / "EBS" / endOfSegments)

  private val inspectCustomerRoute = Route[InspectCustomerPage, String](
    encode = customer => customer.trigram,
    decode = arg => InspectCustomerPage(trigram = arg),
    pattern = root / "inspectCustomer" / segment[String] / endOfSegments
  )

  private val notFoundRoute = Route.static(NotFoundPage, root)

  val router = new Router[Page](
    routes = List(testRESTCallRoute, allCustomersRoute, addCustomerRoute, eventBuStormingRoute, inspectCustomerRoute, notFoundRoute),
    getPageTitle = _.toString,                                                                      // mock page title (displayed in the browser tab next to favicon)
    serializePage = page => page.asJson.noSpaces,                                                   // serialize page data for storage in History API log
    deserializePage = pageStr => decode[Page](pageStr).fold(e => ErrorPage(e.getMessage), identity) // deserialize the above
  )(
    initialUrl = dom.document.location.href, // must be a valid LoginPage or UserPage url
    owner = unsafeWindowOwner,               // this router will live as long as the window
    origin = dom.document.location.origin.get,
    $popStateEvent = windowEvents.onPopState
  )

  private val splitter =
    SplitRender[Page, HtmlElement](router.$currentPage)
      .collectSignal[ErrorPage] { $errorPage =>
        div(
          div("An unpredicted error has just happened. We think this is truly unfortunate."),
          div(
            child.text <-- $errorPage.map(_.error)
          )
        )
      }
      .collectSignal[InspectCustomerPage] { $inspectPage =>
        InspectCustomer.render($inspectPage)
      }
      //.collectStatic(TestRESTCallPage) { TestRESTCall.render }
      .collectStatic(AllCustomersPage) { AllCustomers.render() }
      .collectStatic(AddCustomerPage) { AddCustomer.render }
      .collectStatic(EventBuStormingPage) { EventBuStorming.render }
      .collectStatic(NotFoundPage) { AllCustomers.render() }

  def pushState(page: Page): Unit = {
    router.pushState(page)
  }

  def replaceState(page: Page): Unit = {
    router.replaceState(page)
  }

  val view: Signal[HtmlElement] = splitter.$view

}
