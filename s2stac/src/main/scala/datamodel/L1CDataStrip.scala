package com.azavea.s2stac.datamodel

import com.azavea.s2stac.datamodel.types.{DataPath, DataStripId}

import io.circe._
import io.circe.generic.semiauto._
import io.circe.refined._

final case class L1CDataStrip(
    id: DataStripId,
    path: DataPath
)

object L1CDataStrip {
  implicit val decL1CDataStrip: Decoder[L1CDataStrip] = deriveDecoder
  implicit val encL1CDataStrip: Encoder[L1CDataStrip] = deriveEncoder
}
