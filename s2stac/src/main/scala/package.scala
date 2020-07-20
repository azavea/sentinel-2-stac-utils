package com.azavea

import cats.effect.IO
import com.lightbend.emoji.ShortCodes.Defaults._
import com.lightbend.emoji.ShortCodes.Implicits._
import com.monovore.decline.Argument
import io.estatico.newtype.Coercible

package object s2stac {
  implicit def coercibleArg[R, N](implicit ev: Coercible[Argument[R], Argument[N]], R: Argument[R]) = ev(R)

  def printDebug(s: String): IO[Unit] = IO.delay {
    println(fansi.Color.Green(e":bug: $s"))
  }

  def printInfo(s: String): IO[Unit] = IO.delay {
    println(fansi.Color.Cyan(e":wave: $s"))
  }

  def printWarn(s: String): IO[Unit] = IO.delay {
    println(fansi.Color.Yellow(e":warning: $s"))
  }

  def printError(s: String): IO[Unit] = IO.delay {
    println(fansi.Color.Red(e":x: $s"))
  }
}
