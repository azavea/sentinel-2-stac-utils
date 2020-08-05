package com.azavea.s2stac.datamodel

import com.azavea.s2stac.datamodel.types._

import io.circe.generic.semiauto._
import io.circe.refined._
import io.circe.{Decoder, Encoder}

import java.time.Instant

final case class ProductInfo(
    name: ProductName,
    id: ProductId,
    path: DataPath,
    timestamp: Instant,
    datatakeIdentifier: DatatakeIdentifier,
    sciHubIngestion: Instant,
    s3Ingestion: Instant,
    tiles: List[Tile],
    datastrips: List[DataStrip]
)

object ProductInfo {
  implicit val decProductInfo: Decoder[ProductInfo] = deriveDecoder
  implicit val encProductInfo: Encoder[ProductInfo] = deriveEncoder
}
