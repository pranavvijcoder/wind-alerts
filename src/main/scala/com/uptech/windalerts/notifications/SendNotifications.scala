package com.uptech.windalerts.notifications

import java.io.FileInputStream

import cats.effect.{ExitCode, IO, IOApp, Sync}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.alerts.{AlertsRepository, AlertsService}
import com.uptech.windalerts.domain.domain.BeachId
import com.uptech.windalerts.domain.{FirestoreOps, HttpErrorHandler, beaches, config, secrets, swellAdjustments}
import com.uptech.windalerts.status.{BeachService, Swells, SwellsService, Tides, TidesService, WindsService}
import com.uptech.windalerts.users.{FirestoreUserRepository, UserRepositoryAlgebra}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io.{->, /, GET, Ok, _}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger

import scala.concurrent.duration.Duration
import scala.util.Try

object SendNotifications extends IOApp {

  private val logger = getLogger

  logger.error("Starting")


  val dbWithAuthIO = for {
    projectId <- IO(sys.env("projectId"))
    credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream(s"/app/resources/$projectId.json")))
      .getOrElse(GoogleCredentials.getApplicationDefault))
    options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId(projectId).build)
    _ <- IO(FirebaseApp.initializeApp(options))
    db <- IO(FirestoreClient.getFirestore)
    notifications <- IO(FirebaseMessaging.getInstance)
  } yield (db, notifications)

  val dbWithAuth = dbWithAuthIO.unsafeRunSync()
  implicit val backend = HttpURLConnectionBackend()

  val conf = secrets.read
  val key = conf.surfsUp.willyWeather.key
  val beachSeq = beaches.read
  logger.error(s"beachSeq $beachSeq")
  val adjustments = swellAdjustments.read
  val beachesService = new BeachService[IO](new WindsService[IO](key), new TidesService[IO](key), new SwellsService[IO](key, swellAdjustments.read))
  val alertsRepo: AlertsRepository.Repository = new AlertsRepository.FirestoreAlertsRepository(dbWithAuth._1)

  val alerts = new AlertsService.ServiceImpl(alertsRepo)
  val usersRepo = new FirestoreUserRepository(dbWithAuth._1, new FirestoreOps())

  implicit val httpErrorHandler: HttpErrorHandler[IO] = new HttpErrorHandler[IO]
  val notifications = new Notifications(alerts, beachesService, beachSeq, usersRepo, dbWithAuth._2, httpErrorHandler, new FirestoreNotificationRepository(dbWithAuth._1, new FirestoreOps()), config.read)

  def allRoutes(A: AlertsService.Service, B: BeachService[IO], UR: UserRepositoryAlgebra, firebaseMessaging: FirebaseMessaging, H: HttpErrorHandler[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "notify" => {
      val res = notifications.sendNotification
      val either = res
      either.value.unsafeRunSync() match {
        case Right(_) => Ok()
        case Left(error) => H.handleThrowable(error)
      }
    }
  }.orNotFound


  def run(args: List[String]): IO[ExitCode] = {

    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withResponseHeaderTimeout(Duration(5, "min"))
      .withIdleTimeout(Duration(8, "min"))
      .withHttpApp(allRoutes(alerts, beachesService, usersRepo, dbWithAuth._2, httpErrorHandler))
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

}

class NotificationEndPoints[F[_] : Sync](B: BeachService[F], H: HttpErrorHandler[F]) extends Http4sDsl[F] {
  def allRoutes() = HttpRoutes.of[F] {
    case GET -> Root / "notify" => {
      getStatus(B, id, H)
    case GET -> Root / "v1" / "beaches" / IntVar(id) / "currentStatus" =>
      getStatus(B, id, H)
  }

  private def getStatus(B: BeachService[F], id: Int, H: HttpErrorHandler[F]) = {
    val eitherStatus = for {
      status <- B.get(BeachId(id))
    } yield status
    eitherStatus.value.flatMap {
      case Right(_) => Ok()
      case Left(error) => H.handleThrowable(error)
    }
  }
}