package com.azavea.s2stac.datamodel

import com.azavea.s2stac.datamodel.types.{DataPath, DataStripId}

import io.circe._
import io.circe.generic.semiauto._
import io.circe.refined._

final case class DataStrip(
    id: DataStripId,
    path: DataPath
)

object DataStrip {
  implicit val decDataStrip: Decoder[DataStrip] = deriveDecoder
  implicit val encDataStrip: Encoder[DataStrip] = deriveEncoder
}
