package com.uptech.windalerts.alerts

import cats.data.EitherT
import cats.effect.{Async, ConcurrentEffect, ContextShift, Effect, IO}
import cats.implicits._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.errors.WindAlertError
import com.uptech.windalerts.domain.{conversions, domain}
import io.scalaland.chimney.dsl._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal

import scala.concurrent.ExecutionContext.Implicits.global

trait AlertsRepositoryX[F[_]] {
  def disableAllButOneAlerts(userId: String): F[Seq[AlertT]]

  def getById(id: String): F[Option[AlertT]]

  def getAllForDay(day: Int): F[Seq[AlertT]]

  def getAllForUser(user: String): F[domain.AlertsT]

  def save(alert: AlertRequest, user: String): F[AlertT]

  def delete(requester: String, id: String): EitherT[F, WindAlertError, Unit]

  def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, WindAlertError, AlertT]
}


trait AlertsRepositoryT {
  def disableAllButOneAlerts(userId: String): IO[Seq[AlertT]]

  def getById(id: String): IO[Option[AlertT]]

  def getAllForDay(day: Int): IO[Seq[AlertT]]

  def getAllForUser(user: String): IO[domain.AlertsT]

  def save(alert: AlertRequest, user: String): IO[AlertT]

  def delete(requester: String, id: String): EitherT[IO, WindAlertError, Unit]

  def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[IO, WindAlertError, AlertT]
}

class MongoAlertsRepositoryAlgebra(collection: MongoCollection[AlertT])(implicit cs: ContextShift[IO]) extends AlertsRepositoryT {


  private def findByCriteria(criteria: Bson) =
    IO.fromFuture(IO(collection.find(criteria).toFuture()))


  override def disableAllButOneAlerts(userId: String): IO[Seq[AlertT]] = {
    for {
      all <- getAllForUser(userId)
      updatedIOs <- IO({
        all.alerts.filter(_.enabled) match {
          case Seq() => List[IO[AlertT]]()
          case Seq(only) => List[IO[AlertT]](IO(only))
          case longSeq => longSeq.tail.map(alert => update(alert._id.toHexString, alert.copy(enabled = false)))
        }
      }
      )
      updatedAlerts <- conversions.toIOSeq(updatedIOs)
    } yield updatedAlerts
  }

  private def update(alertId: String, alert: AlertT): IO[AlertT] = {
    IO.fromFuture(IO(collection.replaceOne(equal("_id", new ObjectId(alertId)), alert).toFuture().map(_ => alert)))
  }

  override def getById(id: String): IO[Option[AlertT]] = {
    findByCriteria(equal("_id", new ObjectId(id))).map(_.headOption)
  }

  override def getAllForDay(day: Int): IO[Seq[AlertT]] = {
    findByCriteria(equal("days", day))
  }

  override def getAllForUser(user: String): IO[AlertsT] = {
    findByCriteria(equal("owner", user)).map(AlertsT(_))
  }

  override def save(alertRequest: AlertRequest, user: String): IO[AlertT] = {
    val alert = AlertT(alertRequest, user)

    IO.fromFuture(IO(collection.insertOne(alert).toFuture().map(_ => alert)))
  }

  override def delete(requester: String, alertId: String): EitherT[IO, WindAlertError, Unit] = {
    EitherT.liftF(IO.fromFuture(IO(collection.deleteOne(equal("_id", new ObjectId(alertId))).toFuture().map(_ => ()))))
  }

  override def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest) = {
    val alert = updateAlertRequest.into[AlertT].withFieldComputed(_._id, u => new ObjectId(alertId)).withFieldComputed(_.owner, _ => requester).transform
    EitherT.liftF(IO(collection.replaceOne(equal("_id", new ObjectId(alertId)), alert).toFuture()).map(_ => alert))
  }

}


class MongoAlertsRepositoryAlgebraX[F[_] : ConcurrentEffect](collection: MongoCollection[AlertT])(implicit cs: ContextShift[IO], F: Effect[F]) extends AlertsRepositoryX[F] {


  private def findByCriteria(criteria: Bson): F[Seq[AlertT]] = {
    F.delay(collection.find(criteria).toFuture())
  }


  override def disableAllButOneAlerts(userId: String): F[Seq[AlertT]] = {
    for {
      all <- getAllForUser(userId)
      updated <- F.pure(all.alerts.tail.map(alert => update(alert._id.toHexString, alert.copy(enabled = false))))
    } yield updated

  }

  private def update(alertId: String, alert: AlertT): F[AlertT] = {
    F.delay(collection.replaceOne(equal("_id", new ObjectId(alertId)), alert).toFuture().map(_ => alert))
  }

  override def getById(id: String): F[Option[AlertT]] = {
    findByCriteria(equal("_id", new ObjectId(id))).map(_.headOption)
  }

  override def getAllForDay(day: Int): F[Seq[AlertT]] = {
    findByCriteria(equal("days", day))
  }

  override def getAllForUser(user: String): F[AlertsT] = {
    findByCriteria(equal("owner", user)).map(AlertsT(_))
  }

  override def save(alertRequest: AlertRequest, user: String): F[AlertT] = {
    val alert = AlertT(alertRequest, user)

    F.delay(IO(collection.insertOne(alert).toFuture().map(_ => alert)))
  }

  override def delete(requester: String, alertId: String): EitherT[F, WindAlertError, Unit] = {
    val x = F.delay(collection.deleteOne(equal("_id", new ObjectId(alertId))).toFuture())
    EitherT.liftF(x)
  }

  def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, WindAlertError, AlertT] = {
    val alert = updateAlertRequest.into[AlertT].withFieldComputed(_._id, u => new ObjectId(alertId)).withFieldComputed(_.owner, _ => requester).transform
    val x = F.delay(collection.replaceOne(equal("_id", new ObjectId(alertId)), alert).toFuture())
    EitherT.liftF(x).map(_ => alert)
  }

}