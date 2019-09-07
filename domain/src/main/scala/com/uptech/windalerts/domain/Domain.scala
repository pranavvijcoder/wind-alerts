package com.uptech.windalerts.domain

import java.util

import scala.collection.JavaConverters
import scala.util.control.NonFatal

object Domain {

  final case class BeachId(id: Int) extends AnyVal
  final case class Wind(direction: Double = 0, speed: Double = 0, directionText:String)
  final case class Swell(height: Double = 0, direction: Double = 0, directionText:String)
  final case class TideHeight(status: String)
  final case class Tide(height: TideHeight, swell: Swell)
  final case class Beach(wind: Wind, tide: Tide)
  final case class TimeRange(from: Int, to: Int) {
    def isWithinRange(hour: Int) = from <= hour && to > hour
  }

  object TimeRange {
    def unapply(values: Map[String, Long]) = try {
      Some(TimeRange(values("from").toInt, values("to").toInt))
    }
    catch {
      case NonFatal(ex) => {
        ex.printStackTrace
        None
      }
    }
  }

  case class Alert(
                    owner: String,
                    beachId: Int,
                    days: Seq[Int],
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
  }

  object Alert {

    def unapply(values: Map[String, Any]) = try {
      Some(Alert(
        values("owner").asInstanceOf[String],
        values("beachId").asInstanceOf[Long].toInt,
        j2s(values("days").asInstanceOf[util.ArrayList[Int]]).asInstanceOf[Seq[Int]],
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
        values.get("timeZone").getOrElse("Australia/Sydney").asInstanceOf[String]
      ))
    } catch {
      case NonFatal(ex) => {
        None
      }
    }
  }

  def j2s[A](inputList: util.List[A]) = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

  def j2sm[K, V](map: util.Map[K, V]) = JavaConverters.mapAsScalaMap(map).toMap

}