package com.uptech.windalerts.status

import java.time.Instant
import java.util.UUID
import java.io.FileInputStream
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.io.FileInputStream
import java.time.Instant

import cats.data.{EitherT, Kleisli, OptionT}
import org.http4s.server._
import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.alerts.{Alerts, AlertsRepository}
import com.uptech.windalerts.domain.Domain
import com.uptech.windalerts.domain.Domain.{BeachId, User, UserWithCredentials}
import com.uptech.windalerts.users.{Devices, Users}
import org.http4s.{AuthedRoutes, AuthedService, HttpRoutes, HttpService, Request}
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.server.AuthMiddleware
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import tsec.authentication.TSecBearerToken
import tsec.common.SecureRandomId

import scala.util.Try
import cats.data.{EitherT, Kleisli, OptionT}
import org.http4s.server._
import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.alerts.{Alerts, AlertsRepository}
import com.uptech.windalerts.domain.Domain
import com.uptech.windalerts.domain.Domain.{BeachId, User, UserWithCredentials}
import com.uptech.windalerts.users.{Devices, Users}
import org.http4s.{AuthedRoutes, AuthedService, HttpRoutes, HttpService, Request, _}
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.server.AuthMiddleware
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import tsec.authentication.TSecBearerToken
import tsec.common.SecureRandomId
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.io._


import scala.util.Try
import cats._
import cats.data.OptionT
import cats.effect.{ExitCode, IO, IOApp, Sync}
import cats.implicits._
import org.http4s.dsl.io._
import org.http4s.server.blaze.BlazeServerBuilder
import tsec.authentication._
import tsec.authorization._
import tsec.cipher.symmetric.jca._
import tsec.common.SecureRandomId
import tsec.jws.mac.JWTMac
import tsec.jwt.JWTClaims
import tsec.mac.jca.{HMACSHA256, MacSigningKey}

import scala.collection.mutable
import scala.concurrent.duration._

object ExampleAuthHelpers {
  def dummyBackingStore[F[_], I, V](getId: V => I)(implicit F: Sync[F]) = new BackingStore[F, I, V] {
    private val storageMap = mutable.HashMap.empty[I, V]

    def put(elem: V): F[V] = {
      val map = storageMap.put(getId(elem), elem)
      if (map.isEmpty)
        F.pure(elem)
      else
        F.raiseError(new IllegalArgumentException)
    }

    def get(id: I): OptionT[F, V] =
      OptionT.fromOption[F](storageMap.get(id))

    def update(v: V): F[V] = {
      storageMap.update(getId(v), v)
      F.pure(v)
    }

    def delete(id: I): F[Unit] =
      storageMap.remove(id) match {
        case Some(_) => F.unit
        case None => F.raiseError(new IllegalArgumentException)
      }
  }

  /*
  In our example, we will demonstrate how to use SimpleAuthEnum, as well as
  Role based authorization
   */
  sealed case class Role(roleRepr: String)

  object Role extends SimpleAuthEnum[Role, String] {

    val Guest: Role = Role("Guest")
    val Premium: Role = Role("Premium")

    implicit val E: Eq[Role] = Eq.fromUniversalEquals[Role]

    def getRepr(t: Role): String = t.roleRepr

    protected val values: AuthGroup[Role] = AuthGroup(Guest, Premium)
  }



  val jwtStore =
    dummyBackingStore[IO, SecureRandomId, AugmentedJWT[HMACSHA256, String]](s => SecureRandomId.coerce(s.id))

  val bearerTokenStore =
    dummyBackingStore[IO, SecureRandomId, TSecBearerToken[String]](s => SecureRandomId.coerce(s.id))

  //We create a way to store our users. You can attach this to say, your doobie accessor
  val userStore: BackingStore[IO, String, User] = dummyBackingStore[IO, String, User](_.email)

  val signingKey: MacSigningKey[HMACSHA256] = HMACSHA256.generateKey[Id]


  val settings: TSecTokenSettings = TSecTokenSettings(
    expiryDuration = 10.minutes, //Absolute expiration time
    maxIdle = None
  )

  val jwtStatefulAuth =
    JWTAuthenticator.backed.inBearerToken(
      expiryDuration = 10.minutes, //Absolute expiration time
      maxIdle        = None,
      tokenStore     = jwtStore,
      identityStore  = userStore,
      signingKey     = signingKey
    )

  val bearerTokenAuth =
    BearerTokenAuthenticator(
      bearerTokenStore,
      userStore,
      settings
    )

  val Auth =
    SecuredRequestHandler(jwtStatefulAuth)

  val authservice: TSecAuthService[TSecBearerToken[String], User, IO] = TSecAuthService {
    case GET -> Root asAuthed user =>
      Ok()
  }

//  implicit val decoder = jsonOf[IO, RegisterRequest]

//  def jwtMonadic[F[_]: Sync]: F[JWTMac[HMACSHA256]] =
//    for {
//      key             <- HMACSHA256.generateKey[F]
//      claims          <- JWTClaims.withDuration[F](expiration = Some(10.minutes))
//      jwt             <- JWTMac.build[F, HMACSHA256](claims, key) //You can sign and build a jwt object directly
//      stringjwt       <- JWTMac.buildToString[F, HMACSHA256](claims, key) //Or build it straight to string
//    } yield jwt

//  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
//
//
//      val unauthService: HttpRoutes[IO] = HttpRoutes.of {
//    //Where user is the case class User above
//    case req@POST -> Root / "register" => {
//      val a = for {
//        registerRequest <- req.as[RegisterRequest]
//        jwt <- jwtMonadic
//        //authenticator <- Auth.authenticator.create(registerRequest.email)
//        token <- IO.pure(AugmentedJWT(SecureRandomId.Strong.generate, jwt, registerRequest.email, Instant.now().plusMillis(settings.expiryDuration._1), None))
//        x <- IO(println("x " + token ))
//        y <- IO.pure(jwtStore.put(token))
//      } yield y
//
//      println(a.unsafeRunSync())
//
//      Ok()
//    }

//    case GET -> Root / "token" as user => {
//      val bearerToken = TSecBearerToken(SecureRandomId.Strong.generate, user.email, Instant.now().plusMillis(settings.expiryDuration._1), None)
//      bearerTokenStore.put(bearerToken)
//
//      Ok(TokenResponse(bearerToken.id.toString, bearerToken.expiry).asJson)
//    }


//  }



  /*
  Now from here, if want want to create services, we simply use the following
  (Note: Since the type of the service is HttpService[IO], we can mount it like any other endpoint!):
   */
//  val service: HttpRoutes[IO] = Auth.liftService(TSecAuthService {
//    //Where user is the case class User above
//    case request@GET -> Root / "api" asAuthed user =>
//      /*
//      Note: The request is of type: SecuredRequest, which carries:
//      1. The request
//      2. The Authenticator (i.e token)
//      3. The identity (i.e in this case, User)
//       */
//      val r: SecuredRequest[IO, User, TSecBearerToken[String]] = request
//      Ok()
//  })
//
//  def run(args: List[String]): IO[ExitCode] = {
//    BlazeServerBuilder[IO]
//      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
////            .withHttpApp(allRoutes(alertsService, beaches, users, devices))
//      .withHttpApp(new Routes[F]().routes.orNotFound)
//      .serve
//      .compile
//      .drain
//      .as(ExitCode.Success)
//  }
}
