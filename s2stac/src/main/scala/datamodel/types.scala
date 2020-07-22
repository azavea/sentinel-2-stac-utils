package com.azavea.s2stac.datamodel

import com.azavea.stac4s.extensions.eo.Percentage

import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Interval
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.macros.newtype

import java.util.UUID

object types {
  @newtype case class InventoryPath(value: NonEmptyString)
  @newtype case class OutputCatalogRoot(value: NonEmptyString)
  @newtype case class L1CName(value: NonEmptyString)
  @newtype case class L1CId(value: UUID)
  @newtype case class DataPath(value: NonEmptyString)
  @newtype case class DatatakeIdentifier(value: NonEmptyString)
  @newtype case class DataStripId(value: NonEmptyString)
  @newtype case class CloudCoverage(value: Percentage)
  @newtype case class DataCoverage(value: Percentage)
  @newtype case class ItemId(value: NonEmptyString)
  @newtype case class DayCatalogId(value: NonEmptyString)
  @newtype case class MonthCatalogId(value: NonEmptyString)
  @newtype case class YearCatalogId(value: NonEmptyString)
  @newtype case class SquareCatalogId(value: NonEmptyString)
  @newtype case class BandCatalogId(value: NonEmptyString)
  @newtype case class ZoneCatalogId(value: NonEmptyString)

  type UtmLatitudeBand = String Refined MatchesRegex[W.`"[C-X]"`.T]
  type UtmZone         = Int Refined Interval.Closed[W.`0`.T, W.`60`.T]
}
