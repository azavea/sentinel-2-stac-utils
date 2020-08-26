package com.azavea.s2stac.datamodel

import com.azavea.s2stac.datamodel.types.DataPath
import com.azavea.s2stac.jsonio.{JsonReadError, JsonReader}

import cats.effect.Sync
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString

import java.time.{Instant, LocalDate}

final case class InventoryCsvRow(
    bucket: NonEmptyString,
    key: DataPath,
    size: PosInt,
    lastModified: Instant,
    imageDate: LocalDate
)

object InventoryCsvRow {

  def getProductInfo[F[_]: Sync](reader: JsonReader[F])(row: InventoryCsvRow): F[Either[JsonReadError, ProductInfo]] = {
    val s3Path = DataPath(NonEmptyString.unsafeFrom(s"s3://${row.bucket}/${row.key}"))
    reader.fromPath[ProductInfo](s3Path)
  }

  def getTileInfo[F[_]: Sync](reader: JsonReader[F])(row: InventoryCsvRow): F[Either[JsonReadError, TileInfo]] = {
    val s3Path = DataPath(
      NonEmptyString.unsafeFrom(s"""s3://${row.bucket}/${row.key.value.replace("productInfo", "tileInfo")}""")
    )
    reader.fromPath[TileInfo](s3Path)
  }
}
