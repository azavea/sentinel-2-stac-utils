package com.azavea.s2stac.datamodel

import com.azavea.s2stac.datamodel.types._

import io.circe.generic.semiauto._
import io.circe.refined._
import io.circe.{Decoder, Encoder}

import java.time.Instant

final case class Tile(
    path: DataPath,
    timestamp: Instant,
    utmZone: UtmZone,
    latitudeBand: UtmLatitudeBand,
    gridSquare: String,
    datastrip: DataStrip
)

object Tile {
  implicit val decTile: Decoder[Tile] = deriveDecoder
  implicit val encTile: Encoder[Tile] = deriveEncoder
}
