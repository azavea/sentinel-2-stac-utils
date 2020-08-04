package com.azavea.s2stac

import com.azavea.s2stac.Commands.{CmdProductInfo, CmdTileInfo, CreateCatalog}
import com.azavea.s2stac.crawler.Crawler
import com.azavea.s2stac.datamodel._
import com.azavea.s2stac.datamodel.types.DataPath
import com.azavea.s2stac.jsonio.{SyncJsonReader, SyncJsonWriter}

import cats.effect._
import com.monovore.decline._
import com.monovore.decline.effect._
import eu.timepit.refined.auto._

import java.time.Instant

object HelloWorld
    extends CommandIOApp(
      name = "s2stac",
      header = "Convert a Sentinel-2 inventory file into a STAC-compliant catalog"
    ) {

  override def main: Opts[IO[ExitCode]] =
    (Commands.createCatalogCommand orElse Commands.productInfoCommand orElse Commands.tileInfoCommand)
      .map({
        case CreateCatalog(collection, _, outputCatalogRoot) =>
          Resources.s3[IO].use {
            s3Client =>
              val rows = collection match {
                case L1C =>
                  List(
                    InventoryCsvRow(
                      "sentinel-s2-l1c",
                      DataPath("tiles/48/X/WG/2019/8/21/0/productInfo.json"),
                      1234,
                      Instant.now
                    ),
                    InventoryCsvRow(
                      "sentinel-s2-l1c",
                      DataPath("tiles/48/X/WG/2019/8/21/1/productInfo.json"),
                      1234,
                      Instant.now
                    )
                  )
                case L2A =>
                  List(
                    InventoryCsvRow(
                      "sentinel-s2-l2a",
                      DataPath("tiles/6/U/UG/2019/1/10/0/productInfo.json"),
                      1234,
                      Instant.now
                    ),
                    InventoryCsvRow(
                      "sentinel-s2-l1c",
                      DataPath("tiles/6/U/UG/2019/1/15/0/productInfo.json"),
                      1234,
                      Instant.now
                    )
                  )
              }

              val initialState = CrawlerState.withRemaining(rows)(CrawlerState.initial)
              val crawler =
                new Crawler[IO](
                  new SyncJsonReader[IO](s3Client),
                  new SyncJsonWriter[IO](s3Client),
                  collection,
                  outputCatalogRoot
                )
              crawler.run
                .runS(initialState) flatMap { state =>
                crawler.writeCatalogs(state)
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
