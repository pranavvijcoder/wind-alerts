//package com.uptech.windalerts.status
//
//import cats.effect.Sync
//import org.http4s.HttpRoutes
//import org.http4s.dsl.Http4sDsl
//import tsec.jws.mac.JWTMac
//import tsec.jwt.JWTClaims
//import tsec.mac.jca.HMACSHA256
//import java.time.Instant
//import java.util.UUID
//import java.io.FileInputStream
//import java.time.Instant
//import java.util.concurrent.TimeUnit
//
//import cats.data.{EitherT, Kleisli, OptionT}
//import org.http4s.server._
//import cats.effect.{IO, _}
//import cats.implicits._
//import com.google.auth.oauth2.GoogleCredentials
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.cloud.FirestoreClient
//import com.google.firebase.{FirebaseApp, FirebaseOptions}
//import com.uptech.windalerts.alerts.{Alerts, AlertsRepository}
//import com.uptech.windalerts.domain.Domain
//import com.uptech.windalerts.domain.Domain.{BeachId, RegisterRequest, User, UserWithCredentials}
//import com.uptech.windalerts.users.{Devices, Users}
//import org.http4s.{AuthedRoutes, AuthedService, HttpRoutes, HttpService, Request, _}
//import org.http4s.dsl.impl.Root
//import org.http4s.dsl.io.{->, /, GET, Ok, POST, _}
//import org.http4s.server.AuthMiddleware
//import org.http4s.server.blaze.BlazeServerBuilder
//import org.log4s.getLogger
//import tsec.authentication.TSecBearerToken
//import tsec.common.SecureRandomId
//import io.circe._
//import io.circe.generic.auto._
//import io.circe.syntax._
//import org.http4s.circe._
//import cats._
//import cats.data.OptionT
//import cats.effect.{ExitCode, IO, IOApp, Sync}
//import cats.implicits._
//import com.uptech.windalerts.status.ExampleAuthHelpers.{Auth, jwtStore, settings}
//import org.http4s.server.blaze.BlazeServerBuilder
//import tsec.authentication._
//import tsec.authorization._
//import tsec.cipher.symmetric.jca._
//import tsec.common.SecureRandomId
//import tsec.jws.mac.JWTMac
//import tsec.jwt.JWTClaims
//import tsec.mac.jca.{HMACSHA256, MacSigningKey}
//
//import scala.collection.mutable
//import scala.concurrent.duration._
//import com.uptech.windalerts.domain.DomainCodec._
//
//class Routes[F[_] : Sync]() extends Http4sDsl[F] {
//
//  def jwtMonadic[F[_] : Sync]: F[JWTMac[HMACSHA256]] =
//    for {
//      key <- HMACSHA256.generateKey[F]
//      claims <- JWTClaims.withDuration[F](expiration = Some(10.minutes))
//      jwt <- JWTMac.build[F, HMACSHA256](claims, key) //You can sign and build a jwt object directly
//      stringjwt <- JWTMac.buildToString[F, HMACSHA256](claims, key) //Or build it straight to string
//    } yield jwt
//
//
//  private val value = TSecAuthService[User, HMACSHA256, IO] {
//    //Where user is the case class User above
//    case request@GET -> Root / "api" asAuthed user =>
//      /*
//      Note: The request is of type: SecuredRequest, which carries:
//      1. The request
//      2. The Authenticator (i.e token)
//      3. The identity (i.e in this case, User)
//       */
//      val r: SecuredRequest[F, User, AugmentedJWT[HMACSHA256, Int]] = request
//      Ok()
//  }
//
//  val service: HttpRoutes[F] = Auth.liftService(value)
//
//  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
//
//    //Where user is the case class User above
//    case req@POST -> Root / "register" => {
//      req.decode[RegisterRequest] { registerRequest =>
//        val x = jwtMonadic[F].map(x =>
//          AugmentedJWT[HMACSHA256, String](SecureRandomId.Strong.generate, x.a, registerRequest.email, Instant.now().plusMillis(settings.expiryDuration._1), None)
//        )
//
//        for {
//          user <- x
//          response <- Ok(user.id.toString)
//        } yield response
//      }
//    }
//  }
//
//}
