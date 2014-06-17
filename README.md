Play Google Auth Module
=======================

This module is a very simple implementation of OpenID Connect authentication
for Play 2 applications.

This has been extracted from the OpenID authentication support in Riff-Raff
at the same time that it has been migrated over from OpenID to OpenID Connect.

Adding to your application
--------------------------

In order to add Google authentication to your Play app you must:

 - get a set of API credentials for your app from the [Google Developer Console](https://console.developers.google.com)
 - ensure that you have switched on access to the `Google+ API` for your credentials
 - add play-googleauth to your libraryDependencies
 - create a GoogleAuthConfig instance with your API credentials and callback details (the callback must match both your
 app and the value you set in the developer console)
 - implement a login controller that has actions and routes for the login screen, login action, oauth callback and
 logout
 - implement a small trait that extends com.gu.googleauth.Actions and sets `loginTarget` to be the login action call
 - use `AuthAction` or `NonAuthAction` instead of `Action` to wrap actions in your controllers (these should be made
 available by extending the trait you implemented earlier

Check out the sample application to see how this is done.

Dependencies
------------

Note that this module brings in Apache Commons 1.9 which is later than the version Play requires by default.