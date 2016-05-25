package com.gu.googleauth

import cats.data.{Xor, XorT}
import cats.std.future._
import cats.syntax.applicativeError._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, RequestHeader, Result, Session}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps



