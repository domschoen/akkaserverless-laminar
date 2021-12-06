package starter

import com.raquo.laminar.api.L._
import org.scalajs.dom.document
import starter.components.PageChrome

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.concurrent.ExecutionContext.Implicits.global

@js.native
trait AccountInfo extends js.Object {
  val environment: String      = js.native
  val homeAccountId: String    = js.native
  val idTokenClaims: js.Object = js.native
  val localAccountId: String   = js.native
  val name: String             = js.native
  val tenantId: String         = js.native
  val username: String         = js.native
}
@js.native
trait AuthenticationResult extends js.Object {
  val accessToken: String        = js.native
  val account: AccountInfo       = js.native
  val authority: String          = js.native
  val cloudGraphHostName: String = js.native
  val expiresOn: js.Object       = js.native
  val extExpiresOn: js.Object    = js.native
  val familyId: String           = js.native
  val fromCache: Boolean         = js.native
  val idToken: String            = js.native
  val idTokenClaims: js.Object   = js.native
  val msGraphHost: String        = js.native
  val scopes: js.Array[String]   = js.native
  val state: String              = js.native
  val tenantId: String           = js.native
  val tokenType: String          = js.native
  val uniqueId: String           = js.native
}

// see https://azuread.github.io/microsoft-authentication-library-for-js/ref/classes/_azure_msal_browser.publicclientapplication.html
@js.native
@JSGlobal("msal.PublicClientApplication")
class PublicClientApplication(config: js.Dynamic) extends js.Object {
  def handleRedirectPromise(): js.Promise[AuthenticationResult] = js.native
  def loginRedirect(request: js.Object): js.Promise[Unit]       = js.native
  def getAllAccounts(): js.Array[AccountInfo]                   = js.native
}

object App {
  val userVar = Var(Option.empty[String])

  val loginRequest = js.Dynamic.literal(scopes = js.Array("User.Read"))
  val msalConfig = js.Dynamic.literal(
    auth = js.Dynamic.literal(
      clientId = config.FrontEndConfig.config.msal.clientId,
      authority = config.FrontEndConfig.config.msal.authority,
      redirectUri = config.FrontEndConfig.config.msal.authRedirectUrl
    ),
    cache = js.Dynamic.literal(
      cacheLocation = "sessionStorage", // This configures where your cache will be stored
      storeAuthStateInCookie = false    // Set this to "true" if you are having issues on IE11 or Edge
    )
  )
  val myMSALObj         = new PublicClientApplication(msalConfig)
  val useAuthentication = false

  def main(args: Array[String]): Unit = {
    if (config.FrontEndConfig.config.useAuthentication) {
      starter.App.myMSALObj.loginRedirect(App.loginRequest).toFuture.foreach(_ => println(""))
      val _ = windowEvents.onLoad.foreach(_ => {
        myMSALObj
          .handleRedirectPromise().toFuture
          .foreach { r: AuthenticationResult =>
            val user: Option[String] = if (r == null) {
              val currentAccounts = myMSALObj.getAllAccounts();

              if (currentAccounts.isEmpty) {
                None
              } else if (currentAccounts.size > 1) {
                // Add your account choosing logic here
                println("Multiple accounts detected.");
                None
              } else {
                val username = currentAccounts(0).username;
                Some(username)
              }

            } else {
              Some(r.account.username)
            }
            println("ar " + user)
            userVar.set(user)
            renderAll()
          }
      })(unsafeWindowOwner)
    } else {
      userVar.set(Some(config.FrontEndConfig.config.defaultUser))

      val _ = windowEvents.onLoad.foreach(_ => {
        println("hello " + userVar.now())

        renderAll()
      })(unsafeWindowOwner)
    }
  }

  def renderAll() = {
    val container = document.getElementById("app-container") // This div, its id and contents are defined in index-fastopt.html/index-fullopt.html files
    val _ =
      render(
        container,
        PageChrome(Routes.view)
      )
  }

}
