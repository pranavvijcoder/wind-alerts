package com.uptech.windalerts.domain

object errors {
  trait WindAlertError extends RuntimeException
  case class UserNotFound(userName: String) extends WindAlertError
  case class OperationNotPermitted(message:String) extends WindAlertError
  case class RecordNotFound(message:String) extends WindAlertError
  case class HeaderNotPresent(message:String) extends WindAlertError
  case class UserAlreadyRegistered(message:String) extends WindAlertError
  case class InvalidCredentials(message:String) extends WindAlertError

  case class NoSuchUser(message:String) extends WindAlertError

}