package com.azavea.s2stac.datamodel

import com.azavea.s2stac.datamodel.types._

import io.circe.generic.semiauto._
import io.circe.refined._
import io.circe.{Decoder, Encoder}

import java.time.Instant

final case class L1CTile(
    path: DataPath,
    timestamp: Instant,
    utmZone: UtmZone,
    latitudeBand: UtmLatitudeBand,
    gridSquare: String,
    datastrip: L1CDataStrip
)

object L1CTile {
  implicit val decL1CTile: Decoder[L1CTile] = deriveDecoder
  implicit val encL1CTile: Encoder[L1CTile] = deriveEncoder
}
