package starter.config

import io.circe.derivation.annotations.JsonCodec
import io.circe.parser._
import starter.config

import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.JSImport

@JsonCodec
final case class FrontEndConfig(
  useAuthentication: Boolean,
  defaultUser: String,
  msal: MSALConfig,
  akkaserverless: AkkaserverlessConfig
)

@JsonCodec
final case class MSALConfig(
  authority: String,
  authRedirectUrl: String,
  clientId: String
)

@JsonCodec
final case class AkkaserverlessConfig(
  useCloud: Boolean,
  cloudHostURL: String,
  localHostURL: String
)

object FrontEndConfig {

  @js.native
  @JSImport("frontend-config", JSImport.Namespace)
  private object ConfigGlobalScope extends js.Object {

    val config: js.Object = js.native

  }

  lazy val config: FrontEndConfig = decode[FrontEndConfig](JSON.stringify(ConfigGlobalScope.config)).fold(throw _, identity)

}

object Variables {
  val cloudHostURL = config.FrontEndConfig.config.akkaserverless.cloudHostURL
  val localHostURL = config.FrontEndConfig.config.akkaserverless.localHostURL
  val hostURL      = if (config.FrontEndConfig.config.akkaserverless.useCloud) cloudHostURL else localHostURL
}
