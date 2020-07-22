package com.azavea

import cats.effect.Sync
import com.lightbend.emoji.ShortCodes.Defaults._
import com.lightbend.emoji.ShortCodes.Implicits._
import com.monovore.decline.Argument
import io.estatico.newtype.Coercible

package object s2stac {
  implicit def coercibleArg[R, N](implicit ev: Coercible[Argument[R], Argument[N]], R: Argument[R]) = ev(R)

  def printDebug[F[_]: Sync](s: String): F[Unit] = Sync[F].delay {
    println(fansi.Color.Green(e":bug: $s"))
  }

  def printInfo[F[_]: Sync](s: String): F[Unit] = Sync[F].delay {
    println(fansi.Color.Cyan(e":wave: $s"))
  }

  def printWarn[F[_]: Sync](s: String): F[Unit] = Sync[F].delay {
    println(fansi.Color.Yellow(e":warning: $s"))
  }

  def printError[F[_]: Sync](s: String): F[Unit] = Sync[F].delay {
    println(fansi.Color.Red(e":x: $s"))
  }
}
