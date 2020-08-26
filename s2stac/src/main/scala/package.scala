package com.azavea

import cats.effect.Sync
import cats.syntax.either._
import com.lightbend.emoji.ShortCodes.Defaults._
import com.lightbend.emoji.ShortCodes.Implicits._
import com.monovore.decline.Argument
import io.chrisdavenport.log4cats.Logger
import io.estatico.newtype.Coercible
import kantan.csv.{CellDecoder, DecodeError}

import java.time.{Instant, LocalDate}

package object s2stac {
  implicit def coercibleArg[R, N](implicit ev: Coercible[Argument[R], Argument[N]], R: Argument[R]) = ev(R)

  implicit def coercibleDecoder[R, N](
      implicit ev: Coercible[CellDecoder[R], CellDecoder[N]],
      R: CellDecoder[R]
  ) = ev(R)

  implicit val instantDecoder: CellDecoder[Instant] = CellDecoder[String].emap({ s =>
    Either.catchNonFatal(Instant.parse(s)).leftMap { _ =>
      DecodeError.TypeError(s"Cannot decode $s into an Instant")
    }
  })

  implicit val localDateDecoder: CellDecoder[LocalDate] = CellDecoder[String].emap({ s =>
    Either.catchNonFatal(LocalDate.parse(s)).leftMap { _ =>
      DecodeError.TypeError(s"Cannot decode $s into a LocalDate")
    }
  })

  def printDebug[F[_]: Sync](s: String)(implicit logger: Logger[F]): F[Unit] =
    logger.debug(fansi.Color.Green(e":bug: $s").toString)

  def printInfo[F[_]: Sync](s: String)(implicit logger: Logger[F]): F[Unit] =
    logger.info(fansi.Color.Cyan(e":wave: $s").toString)

  def printWarn[F[_]: Sync](s: String)(implicit logger: Logger[F]): F[Unit] =
    logger.warn(fansi.Color.Yellow(e":warning: $s").toString)

  def printError[F[_]: Sync](s: String)(implicit logger: Logger[F]): F[Unit] =
    logger.error(fansi.Color.Red(e":x: $s").toString)
}
