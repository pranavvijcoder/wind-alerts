package com.uptech.windalerts.domain

import java.util
import io.circe.generic.JsonCodec, io.circe.syntax._
import io.circe.generic.auto._, io.circe.syntax._

import io.circe.generic.JsonCodec
import org.log4s.getLogger

import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.util.control.NonFatal

object domain {
  private val logger = getLogger


  case class UpdateUserRequest(name: String, userType: String, snoozeTill: Long)

  case class UserId(id: String)

  case class UserSettings(userId: String)

  case class TokensWithUser(accessToken: String, refreshToken: String, expiredAt: Long, user: User)

  case class AccessTokenRequest(refreshToken: String)

  case class RefreshToken(refreshToken: String, expiry: Long, userId: String, accessTokenId: String) {
    def isExpired() = System.currentTimeMillis() > expiry
  }

  object RefreshToken {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[RefreshToken] = try {
      val values = tuple._2
      Some(RefreshToken(
        values("refreshToken").asInstanceOf[String],
        values("expiry").asInstanceOf[Long],
        values("userId").asInstanceOf[String],
        values("accessTokenId").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) =>
        None
    }
  }

  @JsonCodec case class FacebookCredentials(id: Option[String], email: String, accessToken: String, deviceType: String)
  object FacebookCredentials {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[Credentials] = try {
      val values = tuple._2
      Some(Credentials(
        Some(tuple._1),
        values("email").asInstanceOf[String],
        values("accessToken").asInstanceOf[String],
        values("deviceType").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) =>
        None
    }
  }

  @JsonCodec case class Credentials(id: Option[String], email: String, password: String, deviceType: String)

  object Credentials {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[Credentials] = try {
      val values = tuple._2
      Some(Credentials(
        Some(tuple._1),
        values("email").asInstanceOf[String],
        values("password").asInstanceOf[String],
        values("deviceType").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) =>
        None
    }
  }

  sealed case class UserType(value: String)

  object UserType {

    object Registered extends UserType("Registered")

    object Trial extends UserType("Trial")

    object TrialExpired extends UserType("TrialExpired")

    object Premium extends UserType("Premium")

    val values = Seq(Registered, Trial, TrialExpired, Premium)

    def apply(value: String): UserType = value match {
      case Registered.value => Registered
      case Trial.value => Trial
      case TrialExpired.value => TrialExpired
      case Premium.value => Premium
    }
  }

  @JsonCodec final case class User(id: String, email: String, name: String, deviceId: String, deviceToken: String, deviceType: String, startTrialAt: Long, userType: String, snoozeTill:Long) {
    def isTrialEnded() = {
      startTrialAt != -1 && startTrialAt < System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
    }
  }

  object User {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[User] = try {
      val values = tuple._2
      Some(User(
        tuple._1,
        values("email").asInstanceOf[String],
        values("name").asInstanceOf[String],
        values("deviceId").asInstanceOf[String],
        values("deviceToken").asInstanceOf[String],
        values("deviceType").asInstanceOf[String],
        values("startTrialAt").asInstanceOf[Long],
        UserType(values("userType").asInstanceOf[String]).value,
        values("snoozeTill").asInstanceOf[Long]
      ))
    }
    catch {
      case NonFatal(_) =>
        None
    }
  }

  @JsonCodec final case class AlertWithUser(alert: Alert, user: User)

  @JsonCodec final case class DeviceRequest(deviceId: String)

  @JsonCodec final case class UserDevices(devices: Seq[UserDevice])

  @JsonCodec final case class UserDevice(deviceId: String, ownerId: String)

  @JsonCodec case class FacebookRegisterRequest(accessToken:String, deviceId: String, deviceType: String, deviceToken: String)

  @JsonCodec case class RegisterRequest(email: String, name: String, password: String, deviceId: String, deviceType: String, deviceToken: String)

  @JsonCodec case class FacebookLoginRequest(accessToken:String, deviceType: String, deviceToken: String)

  @JsonCodec case class LoginRequest(email: String, password: String, deviceType: String, deviceToken: String)

  @JsonCodec case class ChangePasswordRequest(email: String, oldPassword: String, newPassword: String, deviceType: String)

  object UserDevice {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[UserDevice] = try {
      val values = tuple._2
      Some(UserDevice(
        tuple._1,
        values("owner").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) => None
    }
  }

  @JsonCodec final case class BeachId(id: Int) extends AnyVal

  @JsonCodec final case class Wind(direction: Double = 0, speed: Double = 0, directionText: String)

  @JsonCodec final case class Swell(height: Double = 0, direction: Double = 0, directionText: String)

  @JsonCodec final case class TideHeight(height:Double, status: String, nextLow:Long, nextHigh:Long)

  @JsonCodec final case class TideHeightOutput(status:String)
  @JsonCodec final case class SwellOutput(height: Double = 0, direction: Double = 0, directionText: String, nextLow:Long, nextHigh:Long)

  @JsonCodec final case class Tide(height: TideHeightOutput, swell: SwellOutput)

  @JsonCodec final case class Beach(wind: Wind, tide: Tide)

  @JsonCodec case class TimeRange(@BeanProperty from: Int, @BeanProperty to: Int) {
    def isWithinRange(hourAndMinutes: Int): Boolean = from <= hourAndMinutes && to > hourAndMinutes
  }

  object TimeRange {
    def unapply(values: Map[String, Long]): Option[TimeRange] = try {
      Some(new TimeRange(values("from").toInt, values("to").toInt))
    }
    catch {
      case NonFatal(_) => None
    }
  }

  @JsonCodec case class AlertRequest(
                           beachId: Long,
                           days: Seq[Long],
                           swellDirections: Seq[String],
                           timeRanges: Seq[TimeRange],
                           waveHeightFrom: Double,
                           waveHeightTo: Double,
                           windDirections: Seq[String],
                           notificationsPerHour: Long,
                           enabled: Boolean,
                           timeZone: String = "Australia/Sydney")

  case class Alerts(alerts: Seq[Alert])

  @JsonCodec final case class Alert(
                    id: String,
                    owner: String,
                    beachId: Long,
                    days: Seq[Long],
                    swellDirections: Seq[String],
                    timeRanges: Seq[TimeRange],
                    waveHeightFrom: Double,
                    waveHeightTo: Double,
                    windDirections: Seq[String],
                    notificationsPerHour: Long,
                    enabled: Boolean,
                    timeZone: String = "Australia/Sydney") {
    def isToBeNotified(beach: Beach): Boolean = {
      logger.error(s"beach to check $beach")
      logger.error(s"self $swellDirections $waveHeightFrom $waveHeightTo $windDirections")

      swellDirections.contains(beach.tide.swell.directionText) &&
        waveHeightFrom <= beach.tide.swell.height && waveHeightTo >= beach.tide.swell.height &&
        windDirections.contains(beach.wind.directionText)
    }

    def isToBeAlertedAt(minutes: Int): Boolean = timeRanges.exists(_.isWithinRange(minutes))
  }

  object Alert {

    def apply(alertRequest: AlertRequest, user: String): Alert =
      new Alert(
        "",
        user,
        beachId = alertRequest.beachId,
        days = alertRequest.days,
        swellDirections = alertRequest.swellDirections,
        timeRanges = alertRequest.timeRanges,
        waveHeightFrom = alertRequest.waveHeightFrom,
        waveHeightTo = alertRequest.waveHeightTo,
        windDirections = alertRequest.windDirections,
        notificationsPerHour = alertRequest.notificationsPerHour,
        enabled = alertRequest.enabled,
        timeZone = alertRequest.timeZone)

    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[Alert] = try {
      val values = tuple._2
      println(values)
      Some(Alert(
        tuple._1,
        values("owner").asInstanceOf[String],
        values("beachId").asInstanceOf[Long],
        j2s(values("days").asInstanceOf[util.ArrayList[Long]]),
        j2s(values("swellDirections").asInstanceOf[util.ArrayList[String]]),
        {
          val ranges = j2s(values("timeRanges").asInstanceOf[util.ArrayList[util.HashMap[String, Long]]]).map(p => j2sm(p))
            .map(r => {
              val TimeRange(tr) = r
              tr
            })
          ranges
        },
        values("waveHeightFrom").asInstanceOf[Number].doubleValue(),
        values("waveHeightTo").asInstanceOf[Number].doubleValue(),
        j2s(values("windDirections").asInstanceOf[util.ArrayList[String]]),
        values("notificationsPerHour").asInstanceOf[Long],
        values("enabled").asInstanceOf[Boolean],
        values.get("timeZone").getOrElse("Australia/Sydney").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) => None
    }

  }

  case class Notification(id:Option[String], alertId: String, deviceToken:String, title:String, body:String, sentAt:Long)

  object Notification {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[Notification] = try {
      val values = tuple._2
      Some(Notification(
        Some(tuple._1),
        values("alertId").asInstanceOf[String],
        values("deviceToken").asInstanceOf[String],
        values("title").asInstanceOf[String],
        values("body").asInstanceOf[String],
        values("sentAt").asInstanceOf[Long]
      ))
    }
    catch {
      case NonFatal(_) =>
        None
    }
  }
  def j2s[A](inputList: util.List[A]): Seq[A] = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

  def j2sm[K, V](map: util.Map[K, V]): Map[K, V] = JavaConverters.mapAsScalaMap(map).toMap

}
