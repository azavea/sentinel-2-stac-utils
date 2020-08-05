package com.azavea.s2stac.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class BandName(val repr: String) {
  override def toString = repr
}

object BandName {
  case object B01 extends BandName("B01")
  case object B02 extends BandName("B02")
  case object B03 extends BandName("B03")
  case object B04 extends BandName("B04")
  case object B05 extends BandName("B05")
  case object B06 extends BandName("B06")
  case object B07 extends BandName("B07")
  case object B08 extends BandName("B08")
  case object B09 extends BandName("B09")
  case object B10 extends BandName("B10")
  case object B11 extends BandName("B11")
  case object B12 extends BandName("B12")
  case object B8A extends BandName("B8A")

  def fromString(s: String): BandName = s.toUpperCase match {
    case "B01" => B01
    case "B02" => B02
    case "B03" => B03
    case "B04" => B04
    case "B05" => B05
    case "B06" => B06
    case "B07" => B07
    case "B08" => B08
    case "B09" => B09
    case "B10" => B10
    case "B11" => B11
    case "B12" => B12
    case "B8A" => B8A
    case _ =>
      throw new Exception(s"Unsupported band name: ${s}")
  }

  implicit val bandNameEncoder: Encoder[BandName] =
    Encoder.encodeString.contramap[BandName](_.toString)

  implicit val bandNameDecoder: Decoder[BandName] =
    Decoder.decodeString.emap { s =>
      Either.catchNonFatal(fromString(s)).leftMap(_ => "BandName")
    }
}
