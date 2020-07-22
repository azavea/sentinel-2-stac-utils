package com.azavea.s2stac.datamodel

import com.azavea.s2stac.datamodel.types._

import io.circe.generic.semiauto._
import io.circe.refined._
import io.circe.{Decoder, Encoder}

import java.time.Instant

final case class L1CProductInfo(
    name: L1CName,
    id: L1CId,
    path: DataPath,
    timestamp: Instant,
    datatakeIdentifier: DatatakeIdentifier,
    sciHubIngestion: Instant,
    s3Ingestion: Instant,
    tiles: List[L1CTile],
    datastrips: List[L1CDataStrip]
)

object L1CProductInfo {
  implicit val decL1CProductInfo: Decoder[L1CProductInfo] = deriveDecoder
  implicit val encL1CProductInfo: Encoder[L1CProductInfo] = deriveEncoder
}
