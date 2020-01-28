package com.uptech.windalerts.users.subscriptions


import cats.effect.{IO, _}
import cats.implicits._
import com.softwaremill.sttp.sttp
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain.BeachId
import com.uptech.windalerts.domain.{HttpErrorHandler, config, domain, secrets, swellAdjustments}
import com.uptech.windalerts.status.{Beaches, Swells, Tides, Winds}
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import cats.Applicative
import cats.effect.{IO, Sync}
import com.softwaremill.sttp._
import com.uptech.windalerts.domain.config.AppConfig
import com.uptech.windalerts.domain.domain.BeachId
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, parser}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

object  SubscriptionEndpoints extends IOApp {
  private val logger = getLogger
  val appConfig: AppConfig = config.read
  def allRoutes( B: Beaches.Service, H:HttpErrorHandler[IO]) = HttpRoutes.of[IO] {
    case req@POST -> Root / "subscriptions/apple" => {


      val str = req.bodyAsText.compile.foldMonoid.unsafeRunSync()
      sttp.body(str).post(Uri(appConfig.surfsUp.urls.appleVerificationUrl))
      Ok()
    }
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- IO(getLogger.error("Starting"))
      conf <- IO(secrets.read)
      apiKey <- IO(conf.surfsUp.willyWeather.key)
      beaches <- IO(Beaches.ServiceImpl(Winds.impl(apiKey), Swells.impl(apiKey, swellAdjustments.read), Tides.impl(apiKey)))
      httpErrorHandler <- IO(new HttpErrorHandler[IO])
      server <- BlazeServerBuilder[IO]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(allRoutes( beaches, httpErrorHandler))
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    } yield server
  }

}
