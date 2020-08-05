package com.azavea.s2stac

import com.azavea.s2stac.datamodel._
import com.azavea.s2stac.datamodel.types.DataPath
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

  private val productInfoPathOpt: Opts[DataPath] =
    Opts.option[DataPath]("product-info-path", help = "Path to a Sentinel-2 L1C productInfo.json locally or on S3")

  case class CmdProductInfo(inputPath: DataPath)

  val productInfoOpts: Opts[CmdProductInfo] = productInfoPathOpt map { CmdProductInfo }

  private val tileInfoPathOpt: Opts[DataPath] =
    Opts.option[DataPath]("tile-info-path", help = "Path to a Sentinel-2 L1C tileInfo.json locally or on S3")

  case class CmdTileInfo(inputPath: DataPath)

  val tileInfoOpts: Opts[CmdTileInfo] = tileInfoPathOpt map { CmdTileInfo }

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

  val createCatalogCommand = Opts.subcommand("create", "Create a catalog from inventory data") {
    createCatalogOpts
  }

  val productInfoCommand = Opts.subcommand("product-info", "Read product info json") {
    productInfoOpts
  }

  val tileInfoCommand = Opts.subcommand("tile-info", "Read tile info json") {
    tileInfoOpts
  }
}
