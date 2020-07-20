package com.azavea.s2stac.datamodel

import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.macros.newtype

object types {
  @newtype case class InventoryPath(value: NonEmptyString)
  @newtype case class OutputCatalogRoot(value: NonEmptyString)
}
