package com.uptech.windalerts.status

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZonedDateTime}

import cats.effect.IO
import com.softwaremill.sttp._
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{BeachId, TideHeight}
import io.circe.generic.auto._
import io.circe.optics.JsonPath._
import io.circe.parser


trait Tides extends Serializable {
  val alerts: Tides.Service
}

object Tides {

  trait Service {
    def get(beachId: BeachId): IO[domain.TideHeight]
  }

  def impl(apiKey:String): Service = (beachId: BeachId) => {
    val startDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val startDateFormatted = startDateFormat.format(LocalDateTime.now().minusDays(1))
    val request = sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?forecastGraphs=tides&days=3&startDate=$startDateFormatted")
    implicit val backend = HttpURLConnectionBackend()
    val response = request.send()
    val currentTimeGmt = (System.currentTimeMillis()/1000) + ZonedDateTime.now.getOffset.getTotalSeconds
    val eitherResponse = response.body.map(s => {

      val _entries = root.forecastGraphs.tides.dataConfig.series.groups.each.points.each.json.getAll(parser.parse(s).toOption.get)
      val sorted = _entries.flatMap(j => j.as[Datum].toSeq.sortBy(s => {
        s.x
      }))
      val before = sorted.filterNot(s=>s.x > currentTimeGmt)
      val after = sorted.filter(s=>s.x > currentTimeGmt)


      val status = if (after.head.y > after.tail.head.y) "falling" else "rising"

      val nextHigh = after.filter(_.description == "low").head
      val nextLow = after.filter(_.description == "high").head
      TideHeight(before.last.interpolateWith(currentTimeGmt, after.head).y, status, nextLow.x, nextHigh.x)
    })

    val throwableEither = eitherResponse match {
      case Left(s) => Left(new RuntimeException(s))
      case Right(s) => Right(s)
    }
    IO.fromEither(throwableEither)
  }

  case class Datum(
                   x: Long,
                   y: Double,
                   description: String,
                   interpolated:Boolean
                 ) {
    def interpolateWith(newX:Long, other:Datum) =
      Datum(newX, (other.y - y) / (other.x - x) * (newX - x) + y, "", true)
  }

  case class Tide(
                   dateTime: String,
                   height: Double,
                   `type`: String
                 )

}