package com.azavea.s2stac.datamodel

import com.azavea.s2stac.datamodel.types._

import geotrellis.vector.io.json.Implicits._
import geotrellis.vector.{Point, Polygon}
import io.circe.generic.semiauto._
import io.circe.refined._
import io.circe.{Decoder, Encoder}

import java.time.Instant

final case class TileInfo(
    path: DataPath,
    timestamp: Instant,
    utmZone: UtmZone,
    latitudeBand: UtmLatitudeBand,
    gridSquare: String,
    datastrip: DataStrip,
    tileGeometry: GeoJSONWithCRS[Polygon],
    tileDataGeometry: GeoJSONWithCRS[Polygon],
    tileOrigin: GeoJSONWithCRS[Point],
    dataCoveragePercentage: DataCoverage,
    cloudyPixelPercentage: CloudCoverage,
    productName: ProductName,
    productPath: DataPath
)

object TileInfo {
  implicit val decTileInfo: Decoder[TileInfo] = deriveDecoder
  implicit val encTileInfo: Encoder[TileInfo] = deriveEncoder
}
