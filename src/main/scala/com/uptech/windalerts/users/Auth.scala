package com.uptech.windalerts.users

import java.util.concurrent.TimeUnit

import cats.data.{EitherT, OptionT}
import cats.effect.IO
import com.uptech.windalerts.domain.{domain, secrets}
import com.uptech.windalerts.domain.domain.UserType.{Premium, Trial}
import com.uptech.windalerts.domain.domain._
import dev.profunktor.auth.JwtAuthMiddleware
import dev.profunktor.auth.jwt.{JwtAuth, JwtSecretKey, JwtToken}
import io.circe.parser._
import io.netty.handler.ssl.PemPrivateKey
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtHeader}
import io.scalaland.chimney.dsl._

import scala.util.Random

class Auth(refreshTokenRepositoryAlgebra: RefreshTokenRepositoryAlgebra[IO]) {

  val REFRESH_TOKEN_EXPIRY = 7L * 24L * 60L * 60L * 1000L
  val ACCESS_TOKEN_EXPIRY = 1L * 24L * 60L * 60L * 1000L

  case class AccessTokenWithExpiry(accessToken: String, expiredAt: Long)

  private val key = JwtSecretKey("secretKey")
  val jwtAuth = JwtAuth.hmac("secretKey", JwtAlgorithm.HS256)
  val authenticate: JwtClaim => IO[Option[UserId]] = {
        claim => {
          val r = for {
            parseResult <- IO.fromEither(parse(claim.content))
            accessTokenId <- IO.fromEither(parseResult.hcursor.downField("accessTokenId").as[String])
            maybeRefreshToken <- refreshTokenRepositoryAlgebra.getByAccessTokenId(accessTokenId).value
          } yield maybeRefreshToken

          r.map(f=>f.map(t=>UserId(t.userId)))
        }
      }

  val authenticate1: JwtToken => JwtClaim => IO[Option[UserId]] =
    token => authenticate

  val middleware = JwtAuthMiddleware[IO, UserId](jwtAuth, authenticate1)

  def verifyNotExpired(token:String) = {
    val notExpiredOption = for {
      decodedToken <- OptionT.liftF(IO.fromTry(Jwt.decode(token, key.value, Seq(JwtAlgorithm.HS256))))
      notExpired <- isExpired(decodedToken)
      userId <- OptionT(IO(notExpired.subject))
    } yield userId
    notExpiredOption.toRight(TokenExpiredError())
  }

  private def isExpired(decodedToken: JwtClaim) = {
    val isExpired = decodedToken.expiration match {
      case Some(expiry) => {
        if ( System.currentTimeMillis() / 1000 > expiry )
          Some(decodedToken)
        else
          None
      }
      case None => None
    }
    OptionT.liftF(IO(decodedToken))
  }

  def createToken(userId: String, accessTokenId: String): EitherT[IO, ValidationError, AccessTokenWithExpiry] = {
    val current = System.currentTimeMillis()
<<<<<<< Updated upstream
    val expiry = current / 1000 + TimeUnit.MILLISECONDS.toSeconds(ACCESS_TOKEN_EXPIRY)
=======
    val expiry = current / 1000 + TimeUnit.DAYS.toSeconds(expirationInDays)
    val claims = JwtClaim(
      expiration = Some(expiry),
      issuedAt = Some(current / 1000),
      issuer = Some("wind-alerts.com"),
      subject = Some(userId)
    )

    EitherT.right(IO(AccessTokenWithExpiry(Jwt.encode(claims, key.value, JwtAlgorithm.HS256), expiry)))
  }

  def createAppleToken(userId: String, expirationInSeconds: Int, accessTokenId: String): EitherT[IO, ValidationError, AccessTokenWithExpiry] = {

//    String token = Jwts.builder()
//      .setHeaderParam(JwsHeader.KEY_ID, KEY_ID)
//      .setIssuer(TEAM_ID)
//      .setAudience("https://appleid.apple.com")
//      .setSubject(CLIENT_ID)
//      .setExpiration(new Date(System.currentTimeMillis() + (1000 * 60 * 5)))
//      .signWith(pKey, SignatureAlgorithm.ES256)
//      .compact();

    val current = System.currentTimeMillis()
    val expiry = current / 1000 + TimeUnit.SECONDS.toSeconds(expirationInSeconds)
    val claims = JwtClaim(
      expiration = Some(expiry),
      issuedAt = Some(current / 1000),
      issuer = Some("W9WH7WV85S"),
      subject = Some("com.passiondigital.surfsup.ios"),
      audience = Some(Set("https://appleid.apple.com"))
    ) + ("accessTokenId", accessTokenId)

    val encoded = Jwt.encode(JwtHeader.apply(JwtAlgorithm.ES256).withKeyId("7F85H38WBC").toJson, claims.toJson, secrets.key(), JwtAlgorithm.ES256)
    EitherT.right(IO(AccessTokenWithExpiry(encoded, expiry)))
  }

  def createToken(userId: String, expirationInSeconds: Int, accessTokenId: String): EitherT[IO, ValidationError, AccessTokenWithExpiry] = {
    val current = System.currentTimeMillis()
    val expiry = current / 1000 + TimeUnit.SECONDS.toSeconds(expirationInSeconds)
>>>>>>> Stashed changes
    val claims = JwtClaim(
      expiration = Some(expiry),
      issuedAt = Some(current / 1000),
      issuer = Some("wind-alerts.com"),
      subject = Some(userId)
    ) + ("accessTokenId", accessTokenId)

    EitherT.right(IO(AccessTokenWithExpiry(Jwt.encode(claims, key.value, JwtAlgorithm.HS256), expiry)))
  }

  def tokens(accessToken: String, refreshToken: RefreshToken, expiredAt: Long, user:UserT): EitherT[IO, ValidationError, TokensWithUser] =
    EitherT.right(IO(domain.TokensWithUser(accessToken, refreshToken.refreshToken, expiredAt, user.into[UserDTO].withFieldComputed(_.id, u=>u._id.toHexString).transform)))

  def createOtp(n: Int) = {
    val alpha = "0123456789"
    val size = alpha.size

    (1 to n).map(_ => alpha(Random.nextInt.abs % size)).mkString
  }


  def generateRandomString(n: Int) = {
    val alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val size = alpha.size

    (1 to n).map(_ => alpha(Random.nextInt.abs % size)).mkString
  }

  def authorizePremiumUsers(user: domain.UserT):EitherT[IO, ValidationError, UserT] = {
    val either:Either[OperationNotAllowed, UserT] = if (UserType(user.userType) == UserType.Premium || UserType(user.userType) == UserType.Trial) {
      Right(user)
    } else {
      Left(OperationNotAllowed(s"Only ${Premium.value} and ${Trial.value} users can perform this action"))
    }
    EitherT.fromEither(either)
  }
}
