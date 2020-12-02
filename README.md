Play Google Auth Module
=======================

This module is a very simple implementation of OpenID Connect authentication
for Play 2 applications.
It can also be used to get information about the groups of your Google Apps Domain using the Directory API.

Versions
--------

### Supported Play Versions
#### Scala 2.13
* Play **2.8** : use [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gu.play-googleauth/play-v28_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu.play-googleauth/play-v28_2.13)
  ```
  libraryDependencies += "com.gu.play-googleauth" %% "play-v28" % "[maven version number]"
  ```
* Play **2.7** : use [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gu.play-googleauth/play-v27_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu.play-googleauth/play-v27_2.13)
  ```
  libraryDependencies += "com.gu.play-googleauth" %% "play-v27" % "[maven version number]"
  ```
  
#### Scala 2.12

* Play **2.8** : use [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gu.play-googleauth/play-v28_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu.play-googleauth/play-v28_2.12)
  ```
  libraryDependencies += "com.gu.play-googleauth" %% "play-v28" % "[maven version number]"
  ```
* Play **2.7** : use [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gu.play-googleauth/play-v27_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu.play-googleauth/play-v27_2.12)
  ```
  libraryDependencies += "com.gu.play-googleauth" %% "play-v27" % "[maven version number]"
  ```   

We no longer support the below version for Play 2.6, but it can be found here if it is needed:
* Play **2.6** : use [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gu.play-googleauth/play-v26_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu.play-googleauth/play-v26_2.12)
  ```
  libraryDependencies += "com.gu.play-googleauth" %% "play-v26" % "[maven version number]"
  ```

Note that from version **0.7.7** onwards, `play-googleauth` recommends the
use of the [`play-secret-rotation`](https://github.com/guardian/play-secret-rotation)
library, which allows you to rotate your Play [Application Secret](https://www.playframework.com/documentation/2.6.x/ApplicationSecret)
on an active cluster of Play app servers. `play-googleauth` uses the Play
Application Secret to sign the OAuth Anti-Forgery token, and needs to know the
validity over time of your rotated secrets to verify tokens correctly. If you decide
you are willing to take the security risk of _not_ rotating your Application Secret,
you can still use the deprecated `AntiForgeryChecker.borrowSettingsFromPlay(httpConfiguration)`
method.

Adding to your application
--------------------------

In order to add Google authentication to your Play app you must:

 - get a set of API credentials for your app from the [Google Developer Console](https://console.developers.google.com)
 - ensure that you have switched on access to the `Google+ API` for your credentials
 - add play-googleauth to your libraryDependencies
 - create a GoogleAuthConfig instance with your API credentials and callback details (the callback must match both your
 app and the value you set in the developer console)
 - implement a login controller that has actions and routes for the login action, oauth callback, logout and
 a login screen (if required)
 - implement a trait that extends com.gu.googleauth.Actions, sets the appropriate redirect targets and provides an
 `authConfig` from your Google credentials
 - (optionally) configure a Google Group Checker to enforce Google Group membership
 - use `AuthAction` instead of `Action` to wrap actions in your controllers (these should be made available by
 extending the trait you implemented earlier

See the [example](play-v26/src/sbt-test/example/webapp) application to see how this is done.

Caveats
-------

If your login expires and the next request you make is a GET method then your login will be transparently revalidated,
but if your next request is a POST method then it is not possible to sensibly redirect the request and you will end
up being redirected to the correct URL but with a GET request.

AJAX requests will similarly not be re-validated. Two options exist - you can turn off re-validation using the
 `enforceValidity` parameter in `GoogleAuthConfig` or implement a specific ApiAuth endpoint that returns a 419 if your
 session is no longer valid. This can then be used to implement client side logic in an invisible iframe to re-auth.

This module brings in Apache Commons 1.9 which is later than the version Play requires by default. This is
usually fine as it is compatible.

The token acquired from Google is **NOT** cryptographically verified. This is not a problem as it is obtained directly
from Google over an SSL connection, used to authenticate the user and then thrown away. Do not keep the token around
and use it elsewhere unless this code is modified to carry out the verification.

Implement GoogleGroups-based access control using the [Directory API](https://developers.google.com/admin-sdk/directory/)
-------------------------------------------------------------------------------------------------------------------------

You can use the library to check that a user of your domain (e.g. guardian.co.uk) belongs to certain Google Groups. This
is convenient to create an authorisation system in which only people who are members of some groups can access your
application.

In order to be able to use the Directory API, you must first set up your own Service account and make sure it has the
right permissions.

First, log in to your [Google Developer Console](https://console.developers.google.com/), go to your project and create a new service account.
Follow the instructions given [here](https://developers.google.com/identity/protocols/OAuth2ServiceAccount) to create a service account.
When asked to download the public/private key pair, choose the JSON option and keep it in a secure place.

You will then need to contact the administrator of your organisation's Google Apps domain and ask them to authorise your service account to access user data in the Google Apps Domain.

They will need to do so by adding specific [scopes](https://developers.google.com/admin-sdk/directory/v1/guides/authorizing) to the service account.
This is done by granting the *clientId* of the service account with access to the required API scopes.

In order to find out which groups the users of your domain belong to, the only scope you will need is *https://www.googleapis.com/auth/admin.directory.group.readonly*

Finally, you need the email address of a user who has the permission to access the Admin APIs.
It is not enough to be able to authenticate to your domain with a client ID and private key, you also need to specify the email address of one of the organisation's admin users.
This email address should be set up to have specific privileges to read groups.
We call this user the "impersonated user".

As explained in the Google documentation about [Domain-Wide delegation of authority](https://developers.google.com/admin-sdk/directory/v1/guides/delegation):

  > Only users with access to the Admin APIs can access the Admin SDK Directory API, therefore your service account needs to impersonate one of those users to access the Admin SDK Directory API.

Ask the administrator of your organisation's Google Apps domain if you are unsure of what this email address is supposed to be.

Once you have completed those 3 steps, you should be able to integrate it in your application:

  - Make sure that the service account certificate is accessible

  - This is how you can build your credentials from the json cert file you have downloaded:

```scala
import org.apache.commons.io.Charsets.UTF_8
import org.apache.commons.io.IOUtils
import com.google.auth.oauth2.ServiceAccountCredentials

object GoogleAuthConf {
  val impersonatedUser: String = ??? // read from config
  val serviceAccountCert: String = ??? // JSON certificate from Google Developers Console - read from secure storage
}

private val serviceAccount = GoogleServiceAccount(
  GoogleAuthConf.impersonatedUser, // This is the admin user email address we mentioned earlier
  ServiceAccountCredentials.fromStream(IOUtils.toInputStream(serviceAccountCert, UTF_8))
)
```

- You should now be able to retrieve the groups for a given user

```scala
import com.gu.googleauth.GoogleGroupChecker

val checker = new GoogleGroupChecker(serviceAccount)
checker.retrieveGroupsFor(email)
```
