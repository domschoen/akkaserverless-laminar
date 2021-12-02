package starter

import com.raquo.airstream.web.AjaxEventStream
import com.raquo.airstream.web.AjaxEventStream.AjaxStreamError
import com.raquo.laminar.api.L._
import starter.data.Customer
import starter.data.CustomerCreated
import starter.data.CustomerRenamed
import starter.data.CustomerSnapshot
import starter.data.DetailedCustomer
import starter.data.Event
import starter.data.EventEnvelope
import starter.App.userVar
import io.circe._
import io.circe.generic.auto._
import io.circe.derivation.annotations.JsonCodec
import io.circe.parser.decode
import io.circe.syntax._
import com.softwaremill.quicklens._
import starter.config.Variables

import scala.scalajs.js.Date

object DataModel {
  @JsonCodec
  case class FetchedCustomerData(
    name: String = "",
    trigram: String = "",
    customerID: Long = 123,
    customerType: String = "",
    dynamicsAccountID: String = "",
    headCountry: String = "",
    region: String = ""
  )

  case class PostCustomerRenamed(
    trigram: String,
    name: String,
    userID: String
  )

  val diffBus = new EventBus[EventEnvelope]

  def customerListStreamToCustomerStream(stream: EventStream[List[FetchedCustomerData]]) =
    stream.map(cds => EventStream.fromSeq(cds.toSeq)).flatten

  def customerCustomerStreamToEventStream(stream: EventStream[FetchedCustomerData]) =
    stream.map(cd => {
      val customer = DetailedCustomer(
        cd.trigram,
        cd.customerID,
        cd.name,
        cd.customerType,
        cd.dynamicsAccountID,
        cd.headCountry,
        cd.region
      )
      EventEnvelope(CustomerSnapshot(customer), cd.trigram)
    })

  val barWriter: WriteBus[EventEnvelope] = diffBus.writer

  case class CustomerData(customer: DetailedCustomer, trigram: String)

  val $customers: Signal[List[CustomerData]] = diffBus.events.foldLeft(initial = List.empty[CustomerData])((acc, ev) => {
    println("event " + ev)
    ev.event match {
      case CustomerSnapshot(customer) =>
        val filterCustomer: CustomerData => Boolean = _.trigram == ev.trigram
        val existingCustomerOpt                     = acc.find(filterCustomer)
        existingCustomerOpt match {
          case Some(_) =>
            acc
          case None =>
            CustomerData(customer, ev.trigram) :: acc
        }
      case cr: CustomerRenamed =>
        val filterCustomer: CustomerData => Boolean = _.trigram == ev.trigram
        val newAcc                                  = acc.modify(_.eachWhere(filterCustomer).customer.name).setTo(cr.newName)
        val newAcc2                                 = newAcc.modify(_.eachWhere(filterCustomer).customer.events).using(_ :+ cr)
        newAcc2
      case cc: CustomerCreated =>
        val detailedCustomer = DetailedCustomer(
          cc.customer.trigram,
          cc.customer.customerID,
          cc.customer.name,
          cc.customer.customerType,
          cc.customer.dynamicsAccountID,
          cc.customer.headCountry,
          cc.customer.region,
          Seq(cc)
        )
        val filterCustomer: CustomerData => Boolean = _.trigram == ev.trigram
        val existingCustomerOpt                     = acc.find(filterCustomer)
        existingCustomerOpt match {
          case Some(_) =>
            println("Some")
            acc.modify(_.eachWhere(filterCustomer)).setTo(CustomerData(detailedCustomer, ev.trigram))
          case None =>
            println("None")
            CustomerData(detailedCustomer, ev.trigram) :: acc
        }
    }
  })

  def eventReceived(eventEnvelope: EventEnvelope) = {
    //println("WS Received " + event)
    barWriter.onNext(eventEnvelope)
  }

  def sendCustomerRenamedUI() = Observer[(String, String)] { tuple =>
    val newName         = tuple._1
    val trigram         = tuple._2
    val dateMillis      = new Date().getMilliseconds()
    val customerRenamed = starter.data.CustomerRenamed(newName, dateMillis.toLong, userVar.now().get)
    sendEventEnvelope(EventEnvelope(customerRenamed, trigram))
  }

  def sendCustomerRenamed(newName: String, trigram: String): EventStream[Option[String]] = {
    val dateMillis      = new Date().getTime().toLong
    val userID          = userVar.now().get
    val customerRenamed = starter.data.CustomerRenamed(newName, dateMillis, userID)
    println(s"Date $dateMillis")
    val $response = akkaServerlessPostCustomerRenamed(trigram, newName, userID)
    $response.map(resp =>
      resp match {
        case Left(error) => Some(error)
        case Right(ok) =>
          sendEventEnvelope(EventEnvelope(customerRenamed, trigram))
          Some(ok)
      }
    )
  }

  case class AddNewCustomer(
    trigram: String,
    userID: String,
    customerID: Long,
    name: String,
    customerType: String,
    dynamicsAccountID: String,
    headCountry: String,
    region: String
  )

  def sendCustomerCreated(trigram: String,
                          customerID: Long,
                          name: String,
                          customerType: String,
                          dynamicsAccountID: String,
                          headCountry: String,
                          region: String
  ): EventStream[Option[String]] = {
    val userID         = userVar.now().get
    val addNewCustomer = DataModel.AddNewCustomer(trigram, userID, customerID, name, customerType, dynamicsAccountID, headCountry, region)

    val $response = akkaServerlessPostCustomerCreated(addNewCustomer)
    $response.map(resp =>
      resp match {
        case Left(error) => Some(error)
        case Right(_) =>
          val customer        = Customer(trigram, customerID, name, customerType, dynamicsAccountID, headCountry, region)
          val dateMillis      = new Date().getTime().toLong
          val customerRenamed = starter.data.CustomerCreated(customer, dateMillis, userID)
          sendEventEnvelope(EventEnvelope(customerRenamed, trigram))
          None
      }
    )
  }

  val CREATE_CUSTOMERS_URL = Variables.hostURL + "/com.example.CustomerService/CreateCustomer";
  val RENAME_CUSTOMERS_URL = Variables.hostURL + "/com.example.CustomerService/RenameCustomer"
  val RENAME_COUNTRY_URL   = Variables.hostURL + "/com.example.CustomerService/ChangeCustomerCountry"
  val CUSTOMER_DETAILS_URL = Variables.hostURL + "/com.example.view.CustomerDetailsByTrigram/GetCustomer"
  val ALL_CUSTOMERS_URL    = Variables.hostURL + "/com.example.view.AllCustomers/GetAllCustomers"

  def akkaServerlessPostCustomerRenamed(trigram: String, newName: String, userID: String) = {
    val customerRenamed = PostCustomerRenamed(trigram, newName, userID)
    val json            = customerRenamed.asJson.noSpaces
    AjaxEventStream
      .post(
        url = RENAME_CUSTOMERS_URL,
        data = json,
        headers = Map(
          "content-type" -> "application/json"
        )
      )
      .map(req => Right(req.responseText)) // EventStream[String]
      .recover { case err: AjaxStreamError => Some(Left(err.getMessage)) }
  }

  def akkaServerlessPostCustomerCreated(addNewCustomer: AddNewCustomer) = {
    val json = addNewCustomer.asJson.noSpaces
    AjaxEventStream
      .post(
        url = CREATE_CUSTOMERS_URL,
        data = json,
        headers = Map(
          "content-type" -> "application/json"
        )
      )
      .map(req => Right(req.responseText)) // EventStream[String]
      .recover { case err: AjaxStreamError => Some(Left(err.getMessage)) }
  }

  case class PostTrigram(trigram: String)

  def fetchCustomerDetails(trigram: String): EventStream[Either[String, Seq[EventEnvelope]]] = {
    val trigramJson = PostTrigram(trigram)
    val json        = trigramJson.asJson.noSpaces
    AjaxEventStream
      .post(
        url = CUSTOMER_DETAILS_URL,
        data = json,
        headers = Map(
          "content-type" -> "application/json"
        )
      )
      .map(r => parseCustomerDetails(trigram, r.responseText))
      .recover { case err: AjaxStreamError => Some(Left(err.getMessage)) }
  }

  case class LogEvent(action: String, content: String)
  case class CustomerDetails(events: Seq[LogEvent])

  def parseCustomerDetails(trigram: String, response: String) = {
    parser.decode[CustomerDetails](response) match {
      case Right(a) =>
        val eventOpts = a.events.map(rawEvent => {
          //println("Raw event " + rawEvent)
          rawEvent.action match {
            case "com.example.domain.CustomerCreated" =>
              val eventEither = decode[CustomerCreated](rawEvent.content)
              eventEither match {
                case Right(event) =>
                  //println("Event " + event)
                  Some(EventEnvelope(event, trigram))
                case Left(error) =>
                  println("Decoding error " + error.getMessage + " " + error.getCause)
                  None
              }

            case "com.example.domain.CustomerRenamed" =>
              val eventEither = decode[CustomerRenamed](rawEvent.content)
              eventEither match {
                case Right(event) =>
                  //println("Event " + event)
                  Some(EventEnvelope(event, trigram))
                case Left(error) =>
                  println("Decoding error " + error.getMessage + " " + error.getCause)
                  None
              }
          }
        })
        val events = eventOpts.flatten
        Right(events)
      case Left(error) =>
        Left(s"Error parsing fetched customers: ${error.getMessage}")
    }
  }

  def sendEventEnvelope(eventEnvelope: EventEnvelope) = {
    val json = eventEnvelope.asJson.noSpaces
    println("send " + json)
    WebSocketClient.send(json)
  }

  @JsonCodec
  case class AllCustomerData(customers: List[FetchedCustomerData])

  def parseFetchedCustomers(text: String): Either[String, List[FetchedCustomerData]] = {
    parser.decode[AllCustomerData](text) match {
      case Right(a) =>
        val customers = a.customers
        Right(customers)
      case Left(error) =>
        Left(s"Error parsing fetched customers: ${error.getMessage}")
    }

  }

  def fetchCustomers(): EventStream[Either[String, List[FetchedCustomerData]]] = {

    AjaxEventStream
      .post(
        ALL_CUSTOMERS_URL,
        data = "{}",
        headers = Map(
          "content-type" -> "application/json"
        )
      )
      .map(r => {
        println("response " + r.responseText)
        parseFetchedCustomers(r.responseText)
      })
      .recover { case err: AjaxStreamError => Some(Left(err.getMessage)) }
  }

}
