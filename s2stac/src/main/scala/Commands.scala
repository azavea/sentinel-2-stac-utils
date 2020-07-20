package com.azavea.s2stac

import com.azavea.s2stac.datamodel._
import com.azavea.s2stac.datamodel.types.{InventoryPath, OutputCatalogRoot}

import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.refined._

object Commands {

  private val collectionOpt: Opts[S2Collection] = Opts
    .option[String]("collection", help = "Which Sentinel-2 collection to use")
    .mapValidated({
      _.toUpperCase match {
        case "L1C" => L1C.valid
        case "L2A" => L2A.valid
        case s     => s"""$s is not a valid collection. Options are "L1C" and "L2A"""".invalidNel[S2Collection]
      }
    })

  private val inventoryFileOpt: Opts[InventoryPath] = Opts
    .option[InventoryPath](
      "inventory-path",
      help = "Location on S3 or locally of a Sentinel-2 inventory file"
    )

  private val outputCatalogRootOpt: Opts[OutputCatalogRoot] = Opts
    .option[OutputCatalogRoot](
      "output-catalog-root",
      help = "Root prefix locally or S3 to write output Sentinel-2 STAC catalog"
    )

  case class CreateCatalog(
      collection: S2Collection,
      inventoryPath: InventoryPath,
      outputCatalogRoot: OutputCatalogRoot
  )

  val createCatalogOpts = (
    collectionOpt,
    inventoryFileOpt,
    outputCatalogRootOpt
  ).mapN { CreateCatalog }
}
