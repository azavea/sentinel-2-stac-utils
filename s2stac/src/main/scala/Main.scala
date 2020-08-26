package com.azavea.s2stac

import com.azavea.s2stac.Commands.{CmdProductInfo, CmdTileInfo, CreateCatalog}
import com.azavea.s2stac.crawler.Crawler
import com.azavea.s2stac.csvio.SyncCsvReader
import com.azavea.s2stac.datamodel._
import com.azavea.s2stac.datamodel.types.DataPath
import com.azavea.s2stac.jsonio.{JsonReadError, SyncJsonReader, SyncJsonWriter}

import cats.effect._
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import eu.timepit.refined.auto._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import kantan.csv.ReadError
import kantan.csv.generic._
import kantan.csv.refined._
import retry.RetryPolicies

import scala.concurrent.duration._

object Main
    extends CommandIOApp(
      name = "s2stac",
      header = "Convert a Sentinel-2 inventory file into a STAC-compliant catalog"
    ) {

  implicit val logger = Slf4jLogger.getLogger[IO]

  // 390 seconds = 3 + 6 + 12 + 24 + 48 + 96 + 192 seconds with 9 seconds of leeway
  val s3RetryPolicy =
    RetryPolicies.limitRetriesByCumulativeDelay[IO](390.seconds, RetryPolicies.exponentialBackoff[IO](3.seconds))

  override def main: Opts[IO[ExitCode]] =
    (Commands.createCatalogCommand orElse Commands.productInfoCommand orElse Commands.tileInfoCommand)
      .map({
        case CreateCatalog(collection, inputPath, outputCatalogRoot) =>
          Resources.s3[IO].use {
            s3Client =>
              val csvReader = new SyncCsvReader[IO](s3Client, s3RetryPolicy)
              val rowsIO    = csvReader.fromPath[InventoryCsvRow](DataPath(inputPath.value))
              rowsIO flatMap {
                readResults =>
                  val failures     = readResults.collect({ case Left(err) => err })
                  val rows         = readResults.collect({ case Right(row) => row })
                  val initialState = CrawlerState.withRemaining(rows)(CrawlerState.initial)
                  val crawler =
                    new Crawler[IO](
                      new SyncJsonReader[IO](s3Client, s3RetryPolicy),
                      new SyncJsonWriter[IO](s3Client, s3RetryPolicy),
                      collection,
                      outputCatalogRoot
                    )
                  (failures traverse { (err: ReadError) =>
                    printError[IO](err.getMessage)
                  }) *>
                    crawler.run
                      .runS(initialState) flatMap { state =>
                    crawler.writeCatalogs(state) *> {
                      if (state.errorPaths.isEmpty) {
                        IO.pure(ExitCode.Success)
                      } else {
                        printError[IO](
                          s"I had some trouble decoding product or tile info for the following paths:\n ${state.errorPaths
                            .mkString("\n")}"
                        ) map { _ =>
                          ExitCode.Error
                        }
                      }
                    }
                  }
              }

          }
        case CmdProductInfo(inputPath) =>
          Resources.s3[IO].use { s3Client =>
            (new SyncJsonReader[IO](s3Client, s3RetryPolicy)).fromPath[ProductInfo](inputPath)
          } flatMap { (result: Either[JsonReadError, ProductInfo]) =>
            printDebug[IO](s"Debug result for product info read: $result")
          }.map(_.as(ExitCode.Success))

        case CmdTileInfo(inputPath) =>
          Resources.s3[IO].use { s3Client =>
            (new SyncJsonReader[IO](s3Client, s3RetryPolicy)).fromPath[TileInfo](inputPath)
          } flatMap { (result: Either[JsonReadError, TileInfo]) =>
            printDebug[IO](s"Debug result for tile info read: $result")
          }.map(_.as(ExitCode.Success))
      })
}
