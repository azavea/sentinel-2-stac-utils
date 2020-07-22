package com.azavea.s2stac.datamodel

import com.azavea.s2stac.datamodel.types.DataPath
import com.azavea.s2stac.jsonio.JsonReader

import cats.effect.Sync
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Error

import java.time.Instant

final case class InventoryCsvRow(
    bucket: NonEmptyString,
    key: DataPath,
    size: PosInt,
    lastModified: Instant
)

object InventoryCsvRow {

  def getProductInfo[F[_]: Sync](reader: JsonReader[F])(row: InventoryCsvRow): F[Either[Error, L1CProductInfo]] = {
    val s3Path = DataPath(NonEmptyString.unsafeFrom(s"s3://${row.bucket}/${row.key}"))
    reader.fromPath[L1CProductInfo](s3Path)
  }

  def getTileInfo[F[_]: Sync](reader: JsonReader[F])(row: InventoryCsvRow): F[Either[Error, L1CTileInfo]] = {
    val s3Path = DataPath(
      NonEmptyString.unsafeFrom(s"""s3://${row.bucket}/${row.key.value.replace("productInfo", "tileInfo")}""")
    )
    reader.fromPath[L1CTileInfo](s3Path)
  }
}
