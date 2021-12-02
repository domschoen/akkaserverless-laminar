package starter.pages
import com.raquo.airstream.core.Signal
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import starter.DataModel.CustomerData

sealed trait Page {
  def path: String
}
case object TestRESTCallPage extends Page {
  def path: String = "/debug"
}
case object AllCustomersPage extends Page {
  def path: String = "/customers"
}
case object AddCustomerPage extends Page {
  def path: String = "/addCustomer"
}
case object EventBuStormingPage extends Page {
  def path: String = "/EBS"
}

case class InspectCustomerPage(trigram: String) extends Page {
  def path: String = s"/inspectCustomer/$trigram"
}

case class ErrorPage(error: String) extends Page {
  def path: String = "/"
}
case object NotFoundPage extends Page {
  def path: String = "/"
}

object Page {

  implicit val codePage: Codec.AsObject[Page] = deriveCodec[Page]

}
