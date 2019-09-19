package com.uptech.windalerts.domain

import java.util
import java.util.UUID

import com.jmethods.catatumbo.{Embeddable, Entity, Identifier, Property}

import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.util.control.NonFatal

object Domain {

  final case class User(uid:String, email:String, password:String, token:String)


  final case class BeachId(id: Int) extends AnyVal
  final case class Wind(direction: Double = 0, speed: Double = 0, directionText:String)
  final case class Swell(height: Double = 0, direction: Double = 0, directionText:String)
  final case class TideHeight(status: String)
  final case class Tide(height: TideHeight, swell: Swell)
  final case class Beach(wind: Wind, tide: Tide)

  case class TimeRange(from:Int, to:Int) {
    def isWithinRange(hour: Long) = from <= hour && to > hour
  }
  case class AlertRequest(
                           beachId: Long,
                           days: Seq[Long],
                           swellDirections: Seq[String],
                           timeRanges: Seq[TimeRange],
                           waveHeightFrom: Double,
                           waveHeightTo: Double,
                           windDirections: Seq[String],
                           timeZone: String ="Australia/Sydney")

  case class Alerts(alerts:Seq[Alert])

  case class Alert(
                    id : String,
                    owner: String,
                    beachId: Long,
                    days: Seq[Long],
                    swellDirections: Seq[String],
                    timeRanges: Seq[TimeRange],
                    waveHeightFrom: Double,
                    waveHeightTo: Double,
                    windDirections: Seq[String],
                    timeZone: String ="Australia/Sydney") {
    def isToBeNotified(beach: Beach): Boolean = {
      swellDirections.contains(beach.tide.swell.directionText) &&
      waveHeightFrom <= beach.tide.swell.height && waveHeightTo >= beach.tide.swell.height &&
      windDirections.contains(beach.wind.directionText)
    }

    def isToBeAlertedAt(hour: Int) = timeRanges.exists(_.isWithinRange(hour))

    def toBean: AlertBean2 = {
      val alert = new AlertBean2(
        owner,
        beachId,
        new java.util.ArrayList(JavaConverters.asJavaCollection(days)),
        new java.util.ArrayList(JavaConverters.asJavaCollection(swellDirections)),
        new java.util.ArrayList(JavaConverters.asJavaCollection(timeRanges.map(t=>{
          val tr = new TimeRange2()
          tr.from = t.from
          tr.to = t.to
          tr
        }))),
        waveHeightFrom,
        waveHeightTo,
        new java.util.ArrayList(JavaConverters.asJavaCollection(windDirections)),
        timeZone)

      alert
    }
  }

  object Alert {

    def apply(alertRequest: AlertRequest, user:String): Alert =
      new Alert(
        "",
        user,
        alertRequest.beachId,
        alertRequest.days,
        alertRequest.swellDirections,
        alertRequest.timeRanges,
        alertRequest.waveHeightFrom,
        alertRequest.waveHeightTo,
        alertRequest.windDirections,
        alertRequest.timeZone)
  }

  @Embeddable
  class TimeRange2() {
    @Property @BeanProperty var from: Int = 0
    @Property @BeanProperty var to: Int = 0

    def isWithinRange(hour: Long) = from <= hour && to > hour
  }

  @Entity(kind = "alerts")
  class AlertBean2(
                    @Property(name = "owner") @BeanProperty var owner: String,
                    @Property(name = "beachId") @BeanProperty var beachId: Long,
                    @BeanProperty var days: java.util.List[Long],
                    @BeanProperty var swellDirections: java.util.List[String],
                    @BeanProperty  var timeRanges: java.util.List[TimeRange2],
                    @BeanProperty var waveHeightFrom: Double,
                    @BeanProperty var waveHeightTo: Double,
                    @BeanProperty var windDirections: java.util.List[String],
                    @BeanProperty var timeZone: String = "Australia/Sydney") {
    @Identifier var id: String = _

    def this() = this(null, 0, null, null, null, 0, 0, null, null)

    def getId = id

    def setId(id: String): Unit = this.id = id
//
//    def getOwner = owner
//
//    def setOwner(owner: String): Unit = this.owner = owner

    def toAlert :Alert = Alert(id, owner, beachId, j2s(days), j2s(swellDirections), j2s(timeRanges).map(t=>new TimeRange(t.from, t.to)), waveHeightFrom, waveHeightTo, j2s(windDirections), timeZone)

  }


  def j2s[A](inputList: util.List[A]) = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

  def j2sm[K, V](map: util.Map[K, V]) = JavaConverters.mapAsScalaMap(map).toMap

}
