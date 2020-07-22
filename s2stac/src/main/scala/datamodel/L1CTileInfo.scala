package com.azavea.s2stac.datamodel

import com.azavea.s2stac.datamodel.types._

import geotrellis.vector.io.json.Implicits._
import geotrellis.vector.{Point, Polygon}
import io.circe.generic.semiauto._
import io.circe.refined._
import io.circe.{Decoder, Encoder}

import java.time.Instant

final case class L1CTileInfo(
    path: DataPath,
    timestamp: Instant,
    utmZone: UtmZone,
    latitudeBand: UtmLatitudeBand,
    gridSquare: String,
    datastrip: L1CDataStrip,
    tileGeometry: GeoJSONWithCRS[Polygon],
    tileDataGeometry: GeoJSONWithCRS[Polygon],
    tileOrigin: GeoJSONWithCRS[Point],
    dataCoveragePercentage: DataCoverage,
    cloudyPixelPercentage: CloudCoverage,
    productName: L1CName,
    productPath: DataPath
)

object L1CTileInfo {
  implicit val decTileInfo: Decoder[L1CTileInfo] = deriveDecoder
  implicit val encTileInfo: Encoder[L1CTileInfo] = deriveEncoder
}
