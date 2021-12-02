package starter.config

final case class HttpConfiguration(
  interface: String,
  port: Int
)

final case class GooglePubSubConfiguration(
  projectId: String,
  apiKey: String,
  topic: String,
  subscription: String
)

final case class Configuration(
  http: HttpConfiguration,
  pubsub: GooglePubSubConfiguration
)
