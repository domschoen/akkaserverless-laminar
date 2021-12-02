package starter.data
import io.circe.generic.JsonCodec

@JsonCodec
final case class Error(message: String) extends Exception(message)

case class Customer(
  trigram: String,
  customerID: Long,
  name: String,
  customerType: String,
  dynamicsAccountID: String,
  headCountry: String,
  region: String
)

case class DetailedCustomer(
  trigram: String,
  customerID: Long,
  name: String,
  customerType: String,
  dynamicsAccountID: String,
  headCountry: String,
  region: String,
  events: Seq[Event] = Seq.empty[Event]
)

sealed trait Event

case class CustomerRenamed(
  newName: String,
  date: Long,
  byUserID: String
) extends Event

case class CustomerCreated(
  customer: Customer,
  date: Long,
  byUserID: String
) extends Event

case class CustomerSnapshot(
  customer: DetailedCustomer
) extends Event

case class EventEnvelope(event: Event, trigram: String)
