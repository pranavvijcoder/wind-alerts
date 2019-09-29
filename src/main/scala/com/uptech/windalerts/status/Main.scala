package com.uptech.windalerts.status

import java.io.FileInputStream

import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.messaging.{FirebaseMessaging, Message, Notification}
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.alerts.{Alerts, AlertsRepository}
import com.uptech.windalerts.domain.Domain
import com.uptech.windalerts.domain.Domain.BeachId
import com.uptech.windalerts.users.{Devices, Users, UsersRepository}
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger

import scala.util.Try


object Main extends IOApp {

  private val logger = getLogger

  logger.error("Starting")


  val dbWithAuthIO = for {
    credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream("/app/resources/wind-alerts-staging.json")))
      .getOrElse(GoogleCredentials.getApplicationDefault))
    options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build)
    _ <- IO(FirebaseApp.initializeApp(options))
    db <- IO(FirestoreClient.getFirestore)
    auth <- IO(FirebaseAuth.getInstance)
    notifications <- IO(FirebaseMessaging.getInstance)
  } yield (db, auth, notifications)

  val dbWithAuth = dbWithAuthIO.unsafeRunSync()

  val beaches = Beaches.ServiceImpl(Winds.impl, Swells.impl, Tides.impl)
  val alertsRepo:AlertsRepository.Repository = new AlertsRepository.FirebaseBackedRepository(dbWithAuth._1)

  val alerts = new Alerts.ServiceImpl(alertsRepo)
  val users = new Users.FireStoreBackedService(dbWithAuth._2)
  val usersRepo =  new UsersRepository.FirestoreBackedRepository(dbWithAuth._1)

  val devices = new Devices.FireStoreBackedService(dbWithAuth._1)

  def allRoutes(A: Alerts.Service, B: Beaches.Service, U : Users.Service, D:Devices.Service, UR:UsersRepository.Repository, firebaseMessaging: FirebaseMessaging) = HttpRoutes.of[IO] {
    case GET -> Root / "beaches" / IntVar(id) / "currentStatus" =>
      Ok(B.get(BeachId(id)))
    case GET -> Root / "notify" => {
      val usersToBeNotified = for {
        alerts <- A.getAllForDay
        alertsByBeaches <- IO(alerts.groupBy(_.beachId).map(
          kv => {
            (B.get(BeachId(kv._1.toInt)), kv._2)
          }))
        asIOMap <- toIOMap(alertsByBeaches)
        log <- IO(logger.info(s"alertsByBeaches $alertsByBeaches"))
        alertsToBeNotified <- IO(asIOMap.map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1)))))
        usersToBeNotified <- IO(alertsToBeNotified.values.flatMap(elem => elem).map(alert =>  {
          val maybeUser = UR.getById(alert.owner)
          maybeUser.map(userIO=>userIO.map(user => Domain.AlertWithUser(alert, user)))
        }).toList)

        usersToBeNotifiedSeq <- usersToBeNotified.sequence
        log <- IO(logger.info(s"usersToBeNotifiedSeq $usersToBeNotifiedSeq"))
        usersToBeNotifiedFlattend <- IO(usersToBeNotifiedSeq.flatten)

        sendNotifications <- IO(usersToBeNotifiedFlattend.foreach(u=>{
          val msg = Message.builder()
            .setNotification(new Notification(
              s"Wind Alert on ${u.alert.beachId}",
              "Surf Time."))
            .setToken(u.user.deviceToken)
            .build()
          logger.info(s"notifying ${firebaseMessaging.send(msg)}")
        }))
      } yield sendNotifications
      Ok(usersToBeNotified.unsafeRunSync())
    }
    case req@GET -> Root / "alerts" =>
      val alerts = for {
        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
        u <- U.verify(header.value)
        _ <- IO(println(u.getUid))
        resp <- A.getAllForUser(u.getUid)
      } yield (resp)
      Ok(alerts)
    case req@POST -> Root / "alerts" =>
      val alert = for {
        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
        u <- U.verify(header.value)
        alert <- req.as[Domain.AlertRequest]
        resp <- A.save(alert, u.getUid)
      } yield (resp)
      Created(alert)
    case req@DELETE -> Root / "alerts" / alertId =>
      val alert = for {
        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
        u <- U.verify(header.value)
        resp <- A.delete(u.getUid, alertId)
      } yield (resp)
      val res = alert.unsafeRunSync()
      res.toOption.get.unsafeRunSync()
      NoContent()
    case req@PUT -> Root / "alerts"/ alertId =>
      val alert = for {
        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
        u <- U.verify(header.value)
        alert <- req.as[Domain.AlertRequest]
        resp <- A.update(u.getUid, alertId, alert)
      } yield (resp)
      val res = alert.unsafeRunSync()
      Ok(res.toOption.get.unsafeRunSync())
    case req@POST -> Root / "users" / "devices" =>
      val alert = for {
        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
        u <- U.verify(header.value)
        device <- req.as[Domain.DeviceRequest]
        resp <- D.saveDevice(device, u.getUid)
      } yield (resp)
      Created(alert)
    case req@GET -> Root / "users" / "devices" =>
      val devices = for {
        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
        u <- U.verify(header.value)
        resp <- D.getAllForUser(u.getUid)
      } yield (resp)
      Ok(devices)
    case req@DELETE -> Root / "users" / "devices" / deviceId =>
      val device = for {
        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
        u <- U.verify(header.value)
        resp <- D.delete(u.getUid, deviceId)
      } yield (resp)
      val res = device.unsafeRunSync()
      res.toOption.get.unsafeRunSync()
      NoContent()
  }.orNotFound


  private def toIOMap(m: Map[IO[Domain.Beach], Seq[Domain.Alert]]) = {
    m.toList.traverse {
      case (io, s) => io.map(s2 => (s2, s))
    }.map {
      _.toMap
    }
  }

  def run(args: List[String]): IO[ExitCode] = {

    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withHttpApp(allRoutes(alerts, beaches, users, devices, usersRepo, dbWithAuth._3))
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

}