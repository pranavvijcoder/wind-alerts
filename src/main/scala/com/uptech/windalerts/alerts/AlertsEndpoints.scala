package com.uptech.windalerts.alerts

import cats.data.{EitherT, OptionT}
import cats.effect.{Effect, IO}
import com.uptech.windalerts.domain.HttpErrorHandler
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.users.{Auth, UserNotFoundError, UserService, ValidationError}
import io.scalaland.chimney.dsl._
import org.http4s.AuthedRoutes
import org.http4s.dsl.Http4sDsl
import cats.effect.Sync
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import com.uptech.windalerts.domain.codecs._

class AlertsEndpoints[F[_] : Effect](alertService: AlertsService.ServiceX[F], usersService: UserService[F], auth: Auth[F], httpErrorHandler: HttpErrorHandler[F]) extends Http4sDsl[F] {
  def allUsersService(): AuthedRoutes[UserId, F] =
    AuthedRoutes {
      case GET -> Root as user => {
        val resp = alertService.getAllForUser(user.id)
        OptionT.liftF(Ok(resp.asJson))
      }

      case authReq@DELETE -> Root / alertId as user => {
        val action = for {
          eitherDeleted <- alertService.deleteT(user.id, alertId)
        } yield eitherDeleted
        OptionT.liftF({
          action.value.flatMap {
            case Right(x) => NoContent()
            case Left(error) => httpErrorHandler.handleThrowable(new RuntimeException(error))
          }
        })
      }


      case authReq@PUT -> Root / alertId as user => {
        val response = authReq.req.decode[AlertRequest] { alert =>
          val updated = alertService.updateT(user.id, alertId, alert)

          updated.value.flatMap {
            case Right(x) => NoContent()
            case Left(error) => httpErrorHandler.handleThrowable(new RuntimeException(error))
          }

        }
        OptionT.liftF(response)
      }

//      case authReq@POST -> Root as user => {
//        val response = authReq.req.decode[AlertRequest] { alert =>
//
//          val action = for {
//            dbUser <- EitherT.liftF(usersService.getUser(user.id))
//            _ <- auth.authorizePremiumUsersX(dbUser)
//            saved <- EitherT.pure(alertService.save(alert, user.id))
//          } yield saved
//
//
//          action.value.flatMap {
//            case Right(value) => Created(value.into[Alert].withFieldComputed(_.id, u => u._id.toHexString).transform)
//            case Left(error) => httpErrorHandler.handleError(error)
//          }
//        }
//        OptionT.liftF(response)
//      }
    }
}
