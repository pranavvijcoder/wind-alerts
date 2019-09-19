package com.uptech.windalerts.alerts

import java.util
import java.util.Date

import cats.effect.IO
import com.google.cloud.firestore.Firestore
import com.uptech.windalerts.domain.Domain.{Alert, AlertBean2, AlertRequest}

import scala.collection.JavaConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.jmethods.catatumbo.{Entity, EntityManager}
import com.uptech.windalerts.domain.Domain


trait Alerts extends Serializable {
  val alerts: Alerts.Service
}

object Alerts {

  trait Service {
    def getAllForDay: IO[Seq[Alert]]

    def save(alert: AlertRequest, user: String):IO[String]

    def getAllForUser(user:String) : IO[com.uptech.windalerts.domain.Domain.Alerts]
  }

  class FireStoreBackedService(em:EntityManager) extends Service {

    override def save(alert: AlertRequest, user:String): IO[String] = {
      IO(em.insert(Alert(alert, user).toBean).id)
    }

    override def getAllForUser(owner:String): IO[com.uptech.windalerts.domain.Domain.Alerts] = {
      for {
        req <- IO(em.createEntityQueryRequest("SELECT * FROM alerts WHERE timeZone = @timeZone"))
        _ <- IO(req.setNamedBinding("timeZone", owner))
        res <- IO(
            com.uptech.windalerts.domain.Domain.Alerts(
              j2s(em.executeEntityQueryRequest(classOf[AlertBean2], req).getResults).map(_.toAlert)
            )
          )
      } yield res
      }


    override def getAllForDay: IO[Seq[Alert]] = {
      for {
        date <- IO(new Date())
        req <- IO(em.createEntityQueryRequest("SELECT * FROM alerts where days contains = @day"))
        _ <- IO(req.setNamedBinding("day", date.getDay))
        res <- IO(
            j2s(em.executeEntityQueryRequest(classOf[AlertBean2], req).getResults).map(_.toAlert).filter(_.isToBeAlertedAt(date.getHours))
        )
      } yield res
    }

    def j2s[A](inputList: util.List[A]) = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

    def j2s[K, V](map: util.Map[K, V]) = JavaConverters.mapAsScalaMap(map).toMap

    def j2s[A](javaFuture: util.concurrent.Future[A]): Future[A] = {
      Future(javaFuture.get())
    }

  }

}