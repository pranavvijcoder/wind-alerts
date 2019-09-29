package com.uptech.windalerts.alerts

import java.util

import cats.effect.IO
import com.google.cloud.firestore
import com.google.cloud.firestore.{CollectionReference, Firestore, WriteResult}
import com.uptech.windalerts.domain.Domain.{Alert, AlertRequest, TimeRange}

import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AlertsRepository extends Serializable {
  val alerts: AlertsRepository.Repository
}

object AlertsRepository {
  trait Repository {
    def getAllForDay(day:Int) : IO[Seq[Alert]]

    def getAllForUser(user: String): IO[com.uptech.windalerts.domain.Domain.Alerts]

    def save(alert: AlertRequest, user: String): IO[Alert]

    def delete(requester: String, alertId: String): IO[Either[RuntimeException, IO[WriteResult]]]

    def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): IO[Either[RuntimeException, IO[Alert]]]
  }

  class FirebaseBackedRepository(db: Firestore) extends AlertsRepository.Repository {

    private val alerts: CollectionReference = db.collection("alerts")

    override def save(alertRequest: AlertRequest, user: String): IO[Alert] = {
      for {
        document <- IO.fromFuture(IO(j2s(alerts.add(toBean(Alert(alertRequest, user))))))
        alert <- IO(Alert(alertRequest, user).copy(id = document.getId))
      } yield alert
    }

    override def delete(requester: String, alertId: String): IO[Either[RuntimeException, IO[WriteResult]]] = {
      val alert = getById(alertId)
      alert.map(alert => if (alert.owner == requester) Right(delete(alertId)) else Left(new RuntimeException("Fail")))
    }

    private def delete(alertId: String): IO[WriteResult] = {
      IO.fromFuture(IO(j2s(alerts.document(alertId).delete())))
    }

    override def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): IO[Either[RuntimeException, IO[Alert]]] = {
      val alert = getById(alertId)
      alert.map(alert => {
        if (alert.owner == requester) Right(update(alertId, Alert(updateAlertRequest, requester).copy(id = alertId))) else Left(new RuntimeException("Fail"))
      })
    }

    private def update(alertId: String, alert: Alert): IO[Alert] = {
      IO.fromFuture(IO(j2s(alerts.document(alertId).set(toBean(alert))).map(_ => alert)))
    }

    private def getById(id: String): IO[Alert] = {
      for {
        document <- IO.fromFuture(IO(j2s(alerts.document(id).get())))
        alert <- IO({
          val Alert(alert) = (document.getId, j2s(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
          alert
        })
      } yield alert
    }

    override def getAllForUser(user: String): IO[com.uptech.windalerts.domain.Domain.Alerts] = {
      getAllByQuery(alerts.whereEqualTo("owner", user)).map(a=>com.uptech.windalerts.domain.Domain.Alerts(a))
    }

    override def getAllForDay(day: Int): IO[Seq[Alert]] = {
      for {
        all <- getAllByQuery(alerts.whereArrayContains("days", day))
      } yield all
    }

    private def getAllByQuery(query: firestore.Query) = {
      for {
        collection <- IO.fromFuture(IO(j2s(query.get())))
        filtered <- IO(
          j2s(collection.getDocuments)
            .map(document => {
              val Alert(alert) = (document.getId, j2s(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
              alert
            }))
      } yield filtered
    }

    def toBean(alert:Alert): AlertBean = {
      val alertBean = new AlertBean(
        "",
        alert.owner,
        alert.beachId,
        new java.util.ArrayList(JavaConverters.asJavaCollection(alert.days)),
        new java.util.ArrayList(JavaConverters.asJavaCollection(alert.swellDirections)),
        new java.util.ArrayList(JavaConverters.asJavaCollection(alert.timeRanges)),
        alert.waveHeightFrom,
        alert.waveHeightTo,
        new java.util.ArrayList(JavaConverters.asJavaCollection(alert.windDirections)),
        alert.timeZone)
      alertBean
    }

    def j2s[A](inputList: util.List[A]) = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

    def j2s[K, V](map: util.Map[K, V]) = JavaConverters.mapAsScalaMap(map).toMap

    def j2s[A](javaFuture: util.concurrent.Future[A]): Future[A] = {
      Future(javaFuture.get())
    }

  }

  class AlertBean(
                   @BeanProperty var id: String,
                   @BeanProperty var owner: String,
                   @BeanProperty var beachId: Long,
                   @BeanProperty var days: java.util.List[Long],
                   @BeanProperty var swellDirections: java.util.List[String],
                   @BeanProperty var timeRanges: java.util.List[TimeRange],
                   @BeanProperty var waveHeightFrom: Double,
                   @BeanProperty var waveHeightTo: Double,
                   @BeanProperty var windDirections: java.util.List[String],
                   @BeanProperty var timeZone: String ="Australia/Sydney") {}


}