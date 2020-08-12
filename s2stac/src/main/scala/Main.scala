package com.azavea.s2stac

import com.azavea.s2stac.Commands.{CmdProductInfo, CmdTileInfo, CreateCatalog}
import com.azavea.s2stac.crawler.Crawler
import com.azavea.s2stac.datamodel._
import com.azavea.s2stac.datamodel.types.DataPath
import com.azavea.s2stac.csvio.SyncCsvReader
import com.azavea.s2stac.jsonio.{SyncJsonReader, SyncJsonWriter}

import cats.effect._
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import eu.timepit.refined.auto._
import kantan.csv.ReadError
import kantan.csv.generic._
import kantan.csv.refined._

object HelloWorld
    extends CommandIOApp(
      name = "s2stac",
      header = "Convert a Sentinel-2 inventory file into a STAC-compliant catalog"
    ) {

  override def main: Opts[IO[ExitCode]] =
    (Commands.createCatalogCommand orElse Commands.productInfoCommand orElse Commands.tileInfoCommand)
      .map({
        case CreateCatalog(collection, inputPath, outputCatalogRoot) =>
          Resources.s3[IO].use {
            s3Client =>
              val csvReader = new SyncCsvReader[IO](s3Client)
              val rowsIO    = csvReader.fromPath[InventoryCsvRow](DataPath(inputPath.value))
              rowsIO flatMap {
                readResults =>
                  val failures     = readResults.collect({ case Left(err) => err })
                  val rows         = readResults.collect({ case Right(row) => row })
                  val initialState = CrawlerState.withRemaining(rows)(CrawlerState.initial)
                  val crawler =
                    new Crawler[IO](
                      new SyncJsonReader[IO](s3Client),
                      new SyncJsonWriter[IO](s3Client),
                      collection,
                      outputCatalogRoot
                    )
                  (failures traverse { (err: ReadError) =>
                    printError[IO](err.getMessage)
                  }) *>
                    crawler.run
                      .runS(initialState) flatMap { state =>
                    crawler.writeCatalogs(state)
                  }
              }

          }
        case CmdProductInfo(inputPath) =>
          Resources.s3[IO].use { s3Client =>
            (new SyncJsonReader[IO](s3Client)).fromPath[ProductInfo](inputPath)
          } flatMap { result =>
            printDebug[IO](s"Debug result for product info read: $result")
          }
        case CmdTileInfo(inputPath) =>
          Resources.s3[IO].use { s3Client =>
            (new SyncJsonReader[IO](s3Client)).fromPath[TileInfo](inputPath)
          } flatMap { result =>
            printDebug[IO](s"Debug result for tile info read: $result")
          }
      })
      .map(_.as(ExitCode.Success))
}
