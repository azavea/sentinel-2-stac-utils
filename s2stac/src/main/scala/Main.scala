package com.azavea.s2stac

import cats.effect._
import com.monovore.decline._
import com.monovore.decline.effect._

object HelloWorld
    extends CommandIOApp(
      name = "s2stac",
      header = "Convert a Sentinel-2 inventory file into a STAC-compliant catalog"
    ) {

  override def main: Opts[IO[ExitCode]] =
    Commands.createCatalogOpts
      .map({ cmd =>
        printInfo("Here's what you did:") *>
          printDebug(s"Output root: ${cmd.outputCatalogRoot.value}") *>
          printError(s"Inventory path: ${cmd.inventoryPath.value}") *>
          printWarn(s"Collection: ${cmd.collection.repr}")
      })
      .map(_.as(ExitCode.Success))
}
