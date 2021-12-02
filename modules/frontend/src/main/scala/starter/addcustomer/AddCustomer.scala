package starter.addcustomer

import com.raquo.laminar.api.L._
import org.scalajs.dom.ext.KeyCode
import starter.Routes
import starter.components.Link
import starter.pages.AllCustomersPage
import upickle.default.macroRW
import upickle.default.{ReadWriter => RW}
import starter.App.userVar
import starter.DataModel.sendCustomerCreated

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AddCustomer {

  case class FormState(
    name: String = "",
    trigram: String = "",
    userID: String = "",
    customerID: String = "",
    customerType: String = "",
    dynamicsAccountID: String = "",
    headCountry: String = "",
    region: String = "",
    showErrors: Boolean = false
  ) {

    def hasErrors: Boolean = nameError.nonEmpty || trigramError.nonEmpty || userIDError.nonEmpty

    def nameError: Option[String] = {
      if (name.nonEmpty) {
        None
      } else {
        Some("Name is mandatory")
      }
    }

    def trigramError: Option[String] = {
      if (trigram.nonEmpty) {
        None
      } else {
        Some("trigram is mandatory")
      }
    }

    def userIDError: Option[String] = {
      if (userID.nonEmpty) {
        None
      } else {
        Some("userID is mandatory")
      }
    }

    def customerTypeError: Option[String] = {
      if (customerType.nonEmpty) {
        None
      } else {
        Some("customerType is mandatory")
      }
    }

    def customerIDError: Option[String] = {
      if (customerType.nonEmpty) {
        None
      } else {
        Some("customerID is mandatory")
      }
    }

    def dynamicsAccountIDError: Option[String] = {
      if (dynamicsAccountID.nonEmpty) {
        None
      } else {
        Some("dynamicsAccountID is mandatory")
      }
    }
    def headCountryError: Option[String] = {
      if (headCountry.nonEmpty) {
        None
      } else {
        Some("Head Country is mandatory")
      }
    }
    def regionError: Option[String] = {
      if (region.nonEmpty) {
        None
      } else {
        Some("region is mandatory")
      }
    }

    def displayError(error: FormState => Option[String]): Option[String] = {
      error(this).filter(_ => showErrors)
    }
  }
  object FormState {
    implicit def rw: RW[FormState] = macroRW
  }

  val stateVar                = Var(FormState("", "", userVar.now().get, "", "Operator", "", "", "EMEA"))
  val nameWriter              = stateVar.updater[String]((state, name) => state.copy(name = name))
  val trigramWriter           = stateVar.updater[String]((state, trigram) => state.copy(trigram = trigram))
  val userIDWriter            = stateVar.updater[String]((state, userID) => state.copy(userID = userID))
  val customerIDWriter        = stateVar.updater[String]((state, customerID) => state.copy(customerID = customerID))
  val customerTypeWriter      = stateVar.updater[String]((state, customerType) => state.copy(customerType = customerType))
  val dynamicsAccountIDWriter = stateVar.updater[String]((state, dynamicsAccountID) => state.copy(dynamicsAccountID = dynamicsAccountID))
  val headCountryWriter       = stateVar.updater[String]((state, headCountry) => state.copy(headCountry = headCountry))
  val regionWriter            = stateVar.updater[String]((state, region) => state.copy(region = region))

  // Rendering
  def renderInput(error: FormState => Option[String])(mods: Modifier[HtmlElement]*): HtmlElement = {
    val $error = stateVar.signal.map(_.displayError(error))
    div(
      cls := "col-span-6 sm:col-span-3",
      cls.toggle("x-hasError") <-- $error.map(_.nonEmpty),
      p(mods),
      child.maybe <-- $error.map(_.map(err => div(cls("text-red-500"), err)))
    )
  }

  def renderField(fieldName: String, fieldDisplayName: String, error: FormState => Option[String], zoomer: FormState => String, writer: Observer[String]): HtmlElement = {
    renderInput(error)(
      div(
        label(forId := fieldName, cls := "block text-sm font-medium text-gray-700", fieldDisplayName),
        input(
          name := fieldName,
          idAttr := fieldName,
          cls := "border-2 mt-1 focus:ring-indigo-500 focus:border-indigo-500 block w-full shadow-sm sm:text-sm border-gray-300 rounded-md",
          controlled(
            value <-- stateVar.signal.map(zoomer),
            onInput.mapToValue --> writer
          )
        )
      )
    )
  }

  def customerAdded = Observer[Option[String]] { r =>
    println("Creation response " + r)
    r match {
      case Some(_) =>
        $errorVar.set(r)
      case None =>
        Routes.pushState(AllCustomersPage)
    }
  }

  def submitter = Observer[FormState] { state =>
    println(s"state $state has errors ${state.hasErrors}")
    if (state.hasErrors) {
      stateVar.update(_.copy(showErrors = true))
    }
  }

  val $errorVar = Var(Option.empty[String])

  def render: HtmlElement = {
    div(
      form(
        inContext { thisNode =>
          val $response = thisNode
            .events(onSubmit.preventDefault).sample {
              stateVar.signal
            }.map(fs =>
              if (fs.hasErrors) {
                stateVar.update(_.copy(showErrors = true))
                stateVar.now()
              } else {
                fs
              }
            ).filter(fs => !fs.hasErrors).flatMap(fs => {
              sendCustomerCreated(fs.trigram, fs.customerID.toLong, fs.name, fs.customerType, fs.dynamicsAccountID, fs.headCountry, fs.region)
            })
          $response --> customerAdded
        },
        cls := "flex justify-center sm:max-w-xl md:max-w-full md:px-24 lg:px-8 lg:py-10 w-full bg-top bg-cover mt-0 mr-auto mb-0 ml-auto pt-16 pr-4 pb-16 pl-4 component-selected",
        div(
          cls := "lg:grid-cols-2",
          h1(cls := "text-4xl font-normal", "Add New Customer"),
          div(
            cls := "a-wrapper",
            Link(
              AllCustomersPage,
              cls := "mt-1 block px-3 py-2 rounded-md text-base font-medium text-gray-300 hover:text-white hover:bg-gray-700 focus:outline-none focus:text-white focus:bg-gray-700",
              "All Customers"
            ),
            div(
              cls := "px-4 py-5 bg-white sm:p-6",
              div(
                cls := "grid grid-cols-6 gap-6",
                renderField("name", "Name", _.nameError, _.name, nameWriter),
                renderField("trigram", "Trigram", _.trigramError, _.trigram, trigramWriter),
                renderField("userID", "User ID", _.userIDError, _.userID, userIDWriter),
                renderField("customerID", "Customer ID", _.customerIDError, _.customerID, customerIDWriter),
                renderField("customerType", "Customer Type", _.customerTypeError, _.customerType, customerTypeWriter),
                renderField("dynamicsAccountID", "Dynamics Account ID", _.dynamicsAccountIDError, _.dynamicsAccountID, dynamicsAccountIDWriter),
                renderField("headCountry", "Head Country", _.headCountryError, _.headCountry, headCountryWriter),
                renderField("region", "Region", _.regionError, _.region, regionWriter)
              ),
              div(
                span(
                  cls.toggle("x-hasError") <-- $errorVar.signal.map(_.nonEmpty),
                  child.maybe <-- $errorVar.signal.map(_.map(err => div(cls("text-red-500"), err)))
                ),
                button(
                  typ := "submit",
                  cls := "inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium " +
                    "rounded-md text-white " +
                    "hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500",
                  backgroundColor := "#007bff",
                  "Create"
                )
              )
            )
          )
        )
      )
    )
  }

  private val onEnterPress = onKeyPress.filter(_.keyCode == KeyCode.Enter)
}
