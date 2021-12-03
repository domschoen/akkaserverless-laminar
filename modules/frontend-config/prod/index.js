module.exports = {
  config: {
    useAuthentication: true,
    websocketPort: 3500,
    defaultUser: "firstname.lastname@example.com",
    msal: {
        authRedirectUrl: "http://localhost:3000/",
        authority: "https://login.microsoftonline.com/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxx",
        clientId: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxx"
    },
    akkaserverless: {
      useCloud: true,
      cloudHostURL: "https://super-star-1234.us-east1.akkaserverless.app",
      localHostURL: "http://localhost:9000"
    }
  },
  externals: {
    MsalModule : '@azure/msal-browser'
  }
}
