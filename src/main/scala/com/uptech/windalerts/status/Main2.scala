package com.uptech.windalerts.status

import java.io.FileInputStream
import java.util

import cats.effect.IO
import com.google.auth.oauth2.GoogleCredentials
import com.google.common.collect.{Lists, Sets}
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.jmethods.catatumbo.{Embeddable, Embedded, Entity, EntityManager, EntityManagerFactory, Identifier, Property}
import com.uptech.windalerts.domain.Domain.{Alert, AlertBean, TimeRange}
import io.circe.Json
import io.circe.syntax._

import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.concurrent.Future
import scala.util.Try

object Main2 extends App {



}
