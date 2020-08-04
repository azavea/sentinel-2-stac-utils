package com.azavea.s2stac.crawler

import com.azavea.s2stac.datamodel.types._
import com.azavea.s2stac.datamodel.{CrawlerState, History, InventoryCsvRow, ProductInfo, TileInfo}
import com.azavea.s2stac.jsonio.{JsonReader, JsonWriter}
import com.azavea.s2stac.{printInfo, printWarn}
import com.azavea.stac4s._
import com.azavea.stac4s.extensions.eo.{Band, EOAssetExtension, EOItemExtension}
import com.azavea.stac4s.syntax._

import cats.Parallel
import cats.data.{NonEmptyList, StateT}
import cats.effect.Sync
import cats.implicits._
import eu.timepit.refined._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.proj4.LatLng
import geotrellis.vector.methods.Implicits._
import geotrellis.vector.reproject.Implicits._
import io.circe.refined._
import io.circe.syntax._

import java.time.{Instant, ZoneId, ZonedDateTime}

class Crawler[F[_]: Sync: Parallel](reader: JsonReader[F], writer: JsonWriter[F], catalogRoot: OutputCatalogRoot) {

  val l1cBucket = "sentinel-s2-l1c"

  // stashed to need it _eventually_
  val rootCollection = StacCollection(
    "1.0.0-beta1",
    Nil,
    s"Sentinel-2 Scenes",
    Some(s"Sentinel-2 Scenes"),
    s"Sentinel-2 Scenes",
    List("sentinel-2"),
    Proprietary(),
    List(
      StacProvider("ESA", None, List(Producer), Some("https://earth.esa.int/web/guest/home")),
      StacProvider("Synergize", None, List(Processor), Some("https://registry.opendata.aws/sentinel-2/")),
      StacProvider("AWS", None, List(Host), Some("http://sentinel-pds.s3-website.eu-central-1.amazonaws.com/")),
      StacProvider("Azavea", None, List(Processor), Some("https://github.com/azavea/sentinel-2-stac-utils"))
    ),
    StacExtent(
      SpatialExtent(
        List(TwoDimBbox(-180, -90, 180, 90))
      ),
      Interval(List(TemporalExtent(Instant.parse("2013-06-01T00:00:00Z"), None)))
    ),
    // for summaries, in principle we could include counts of things in CrawlerState and track them as we
    // encounter items we need to produce, but that's an adventure for another time
    ().asJsonObject,
    ().asJsonObject,
    Nil
  )

  private val s2Bands = NonEmptyList.of(
    Band("B01", Some("coastal"), None, None, None),
    Band("B02", Some("blue"), None, None, None),
    Band("B03", Some("green"), None, None, None),
    Band("B04", Some("red"), None, None, None),
    Band("B05", None, None, None, None),
    Band("B06", None, None, None, None),
    Band("B07", None, None, None, None),
    Band("B08", Some("nir"), None, None, None),
    Band("B8A", Some("nir08"), None, None, None),
    Band("B09", Some("nir09"), None, None, None),
    Band("B10", Some("cirrus"), None, None, None),
    Band("B11", Some("swir16"), None, None, None),
    Band("B12", Some("swir22"), None, None, None)
  )

  private val trueColorBands = {
    val bandList = s2Bands.toList
    NonEmptyList.of(bandList(3), bandList(2), bandList(1))
  }

  def run: StateT[F, CrawlerState, CrawlerState] = StateT { step }

  private def step(crawlerState: CrawlerState): F[(CrawlerState, CrawlerState)] = {
    crawlerState.remaining match {
      case Nil => Sync[F].pure((crawlerState, crawlerState))
      case h :: t =>
        for {
          _            <- printInfo[F](s"Head bucket: ${h.bucket}")
          productInfoE <- getProductInfo(h)
          tileInfoE    <- getTileInfo(h)
          newState <- (productInfoE, tileInfoE).tupled match {
            case Right((productInfo, tileInfo)) =>
              writeItem(crawlerState, productInfo, tileInfo)
            case Left(_) =>
              printWarn(s"Couldn't decode one of product info or tile info for ${h.key}") map { _ =>
                crawlerState
              }
          }
          next <- step(
            CrawlerState.withRemaining(t)(newState)
          )
        } yield next
    }
  }

  private val getProductInfo = InventoryCsvRow.getProductInfo(reader) _
  private val getTileInfo    = InventoryCsvRow.getTileInfo(reader) _

  def writeCatalogs(history: CrawlerState): F[Unit] =
    List(
      writeFirstParentCatalogs(history),
      writeMonthCatalogs(history),
      writeYearCatalogs(history),
      writeSquareCatalogs(history),
      writeBandCatalogs(history),
      writeZoneCatalogs(history),
      writeRootCollection(history)
    ).parSequence map { _ =>
      ()
    }

  private def writeItem(history: CrawlerState, productInfo: ProductInfo, tileInfo: TileInfo): F[CrawlerState] = {

    // for each level of nesting
    // build the relevant history
    // check if it's in crawler state
    // if it is return None
    // if its' not return `IO[<CrawlerState.foo>]` with the `foo` for that level
    // mash all the IOs together with combineAll ???
    val bandHistory       = History.BandHistory(tileInfo.utmZone, tileInfo.latitudeBand)
    val gridSquareHistory = History.GridSquareHistory(bandHistory, tileInfo.gridSquare)
    val yearHistory       = History.YearHistory(gridSquareHistory, getYear(tileInfo.timestamp))
    val monthHistory      = History.MonthHistory(yearHistory, getMonth(tileInfo.timestamp))
    val dayHistory        = History.DayHistory(monthHistory, getDay(tileInfo.timestamp))
    val item              = makeItem(productInfo, tileInfo)

    val itemChildState =
      CrawlerState.dayCatalogChild(DayCatalogId(dayHistory.toID), ItemId(NonEmptyString.unsafeFrom(item.id)))

    printInfo[F](s"Writing root collection ")
    printInfo[F](s"Writing item ${item.id}") *>
      writer
        .toPath[StacItem](
          DataPath(NonEmptyString.unsafeFrom(s"${dayHistory.toPath(catalogRoot)}/items/${item.id}.json")),
          item
        ) *>
      List(
        makeUtmZoneParent(history, tileInfo.utmZone),
        makeUtmLatitudeBandParent(history, bandHistory),
        makeGridSquareParent(history, gridSquareHistory),
        makeYearParent(history, yearHistory),
        makeMonthParent(history, monthHistory),
        makeDayParent(history, dayHistory)
      ).sequence map { newParents =>
      (history +: itemChildState +: newParents).combineAll
    }
  }

  // use catalog child links and catalogs in the id -> catalog map to
  // write the first parent catalogs and all of their links to children
  private def writeFirstParentCatalogs(history: CrawlerState): F[Unit] = {
    val linkMap = history.dayChildLinks.mapValues { itemIds =>
      itemIds map { itemId =>
        StacLink(s"./items/${itemId.value}.json", StacLinkType.Child, Some(`application/json`), None)
      }
    }
    history.days.keySet.toList traverse { (dayHistory: History.DayHistory) =>
      val baseCatalog          = history.days(dayHistory)
      val links: Set[StacLink] = linkMap(DayCatalogId(dayHistory.toID))
      writer.toPath(
        DataPath(NonEmptyString.unsafeFrom(s"${dayHistory.toPath(catalogRoot)}/catalog.json")),
        baseCatalog.copy(links = (baseCatalog.links.toSet | links).toList)
      )
    } map { _ =>
      ()
    }
  }

  private def writeMonthCatalogs(history: CrawlerState): F[Unit] = {
    val linkMap = history.monthChildLinks.mapValues { dayHistories =>
      dayHistories map { dayHistory =>
        StacLink(f"./${dayHistory.day}%02d/catalog.json", StacLinkType.Child, Some(`application/json`), None)
      }
    }
    history.months.keySet.toList traverse { (monthHistory: History.MonthHistory) =>
      val baseCatalog          = history.months(monthHistory)
      val links: Set[StacLink] = linkMap(MonthCatalogId(monthHistory.toID))
      writer.toPath(
        DataPath(NonEmptyString.unsafeFrom(s"${monthHistory.toPath(catalogRoot)}/catalog.json")),
        baseCatalog.copy(links = (baseCatalog.links.toSet | links).toList)
      )
    } map { _ =>
      ()
    }
  }

  private def writeYearCatalogs(history: CrawlerState): F[Unit] = {
    val linkMap = history.yearChildLinks.mapValues { monthHistories =>
      monthHistories map { monthHistory =>
        StacLink(f"./${monthHistory.month}%02d/catalog.json", StacLinkType.Child, Some(`application/json`), None)
      }
    }

    history.years.keySet.toList traverse { (yearHistory: History.YearHistory) =>
      val baseCatalog          = history.years(yearHistory)
      val links: Set[StacLink] = linkMap(YearCatalogId(yearHistory.toID))
      writer.toPath(
        DataPath(NonEmptyString.unsafeFrom(s"${yearHistory.toPath(catalogRoot)}/catalog.json")),
        baseCatalog.copy(links = (baseCatalog.links.toSet | links).toList)
      )
    } map { _ =>
      ()
    }

  }

  private def writeSquareCatalogs(history: CrawlerState): F[Unit] = {
    val linkMap = history.squareChildLinks.mapValues { yearHistories =>
      yearHistories map { yearHistory =>
        StacLink(s"./${yearHistory.year}/catalog.json", StacLinkType.Child, Some(`application/json`), None)
      }
    }

    history.utmGridSquares.keySet.toList traverse { (squareHistory: History.GridSquareHistory) =>
      val baseCatalog          = history.utmGridSquares(squareHistory)
      val links: Set[StacLink] = linkMap(SquareCatalogId(squareHistory.toID))
      writer.toPath(
        DataPath(NonEmptyString.unsafeFrom(s"${squareHistory.toPath(catalogRoot)}/catalog.json")),
        baseCatalog.copy(links = (baseCatalog.links.toSet | links).toList)
      )
    } map { _ =>
      ()
    }
  }

  private def writeBandCatalogs(history: CrawlerState): F[Unit] = {
    val linkMap = history.bandChildLinks.mapValues { squareHistories =>
      squareHistories map { squareHistory =>
        StacLink(s"./${squareHistory.square}/catalog.json", StacLinkType.Child, Some(`application/json`), None)
      }
    }

    history.utmBands.keySet.toList traverse { (bandHistory: History.BandHistory) =>
      {
        val baseCatalog          = history.utmBands(bandHistory)
        val links: Set[StacLink] = linkMap(BandCatalogId(bandHistory.toID))
        writer.toPath(
          DataPath(NonEmptyString.unsafeFrom(s"${bandHistory.toPath(catalogRoot)}/catalog.json")),
          baseCatalog.copy(links = (baseCatalog.links.toSet | links).toList)
        )
      }
    } map { _ =>
      ()
    }
  }

  private def writeZoneCatalogs(history: CrawlerState): F[Unit] = {
    val linkMap = history.zoneChildLinks.mapValues { bandHistories =>
      bandHistories map { bandHistory =>
        StacLink(s"./${bandHistory.band}/catalog.json", StacLinkType.Child, Some(`application/json`), None)
      }
    }

    history.utmZones.keySet.toList traverse { (zone: UtmZone) =>
      {
        val baseCatalog          = history.utmZones(zone)
        val links: Set[StacLink] = linkMap(ZoneCatalogId(NonEmptyString.unsafeFrom(s"$zone")))
        writer.toPath(
          DataPath(NonEmptyString.unsafeFrom(s"$catalogRoot/$zone/catalog.json")),
          baseCatalog.copy(links = (baseCatalog.links.toSet | links).toList)
        )
      }
    } map { _ =>
      ()
    }
  }

  private def writeRootCollection(history: CrawlerState): F[Unit] = {
    val zoneLinks = history.utmZones.keySet map { zone =>
      StacLink(s"./$zone/catalog.json", StacLinkType.Child, Some(`application/json`), None)
    }

    writer
      .toPath[StacCollection](
        DataPath(NonEmptyString.unsafeFrom(s"${catalogRoot.value}/collection.json")),
        rootCollection.copy(links = zoneLinks.toList)
      )
  }

  private def makeUtmZoneParent(history: CrawlerState, zone: UtmZone): F[CrawlerState] =
    if (history.utmZones.contains(zone)) {
      printInfo[F](s"Zone $zone already exists") *>
        Sync[F].pure(CrawlerState.initial)
    } else {
      val zoneCatalog = StacCatalog(
        "1.0.0-beta1",
        Nil,
        s"catalog-zone-$zone",
        None,
        s"Catalog holding descendants of UTM Zone $zone",
        List(
          StacLink("../collection.json", StacLinkType.Parent, Some(`application/json`), None),
          StacLink("../collection.json", StacLinkType.StacRoot, Some(`application/json`), None)
        )
      )
      printInfo[F](s"Zone $zone did not exist!") map { _ =>
        CrawlerState.zoneHistory(zone, zoneCatalog)
      }
    }

  private def makeUtmLatitudeBandParent(history: CrawlerState, band: History.BandHistory): F[CrawlerState] =
    if (history.utmBands.contains(band)) {
      printInfo[F](s"Band ${band.toID} already exists") *>
        Sync[F].pure(CrawlerState.initial)
    } else {
      val bandCatalog = StacCatalog(
        "1.0.0-beta1",
        Nil,
        s"catalog-band-${band.toID}",
        None,
        s"Catalog holding descendants of UTM Band ${band.band}",
        List(
          StacLink("../catalog.json", StacLinkType.Parent, Some(`application/json`), None),
          StacLink("../../collection.json", StacLinkType.StacRoot, Some(`application/json`), None)
        )
      )
      printInfo[F](s"Band ${band.toID} did not exit!") map { _ =>
        CrawlerState.bandHistory(band, bandCatalog) `combine` CrawlerState.zoneCatalogChild(
          ZoneCatalogId(NonEmptyString.unsafeFrom(s"${band.zone}")),
          band
        )
      }
    }

  private def makeGridSquareParent(history: CrawlerState, square: History.GridSquareHistory): F[CrawlerState] =
    if (history.utmGridSquares.contains(square)) {
      printInfo[F](s"Square ${square.toID} already exists") *>
        Sync[F].pure(CrawlerState.initial)
    } else {
      val squareCatalog = StacCatalog(
        "1.0.0-beta1",
        Nil,
        s"catalog-square-${square.toID}",
        None,
        s"Catalog holding descendants of UTM Square ${square.square}",
        List(
          StacLink("../catalog.json", StacLinkType.Parent, Some(`application/json`), None),
          StacLink("../../../collection.json", StacLinkType.StacRoot, Some(`application/json`), None)
        )
      )
      printInfo[F](s"Square ${square.toID} did not exist!") map { _ =>
        CrawlerState.gridSquareHistory(square, squareCatalog) `combine` CrawlerState.bandCatalogChild(
          BandCatalogId(square.band.toID),
          square
        )
      }
    }

  private def makeYearParent(history: CrawlerState, yearHistory: History.YearHistory) =
    if (history.years.contains(yearHistory)) {
      printInfo[F](s"Year ${yearHistory.toID} already exists") *>
        Sync[F].pure(CrawlerState.initial)
    } else {
      val yearCatalog = StacCatalog(
        "1.0.0-beta1",
        Nil,
        s"catalog-year-${yearHistory.toID}",
        None,
        s"Catalog holding descendants of year ${yearHistory.year}",
        List(
          StacLink("../catalog.json", StacLinkType.Parent, Some(`application/json`), None),
          StacLink("../../../../collection.json", StacLinkType.StacRoot, Some(`application/json`), None)
        )
      )
      printInfo[F](s"Year ${yearHistory.toID} did not exist!") map { _ =>
        CrawlerState.yearHistory(yearHistory, yearCatalog) `combine` CrawlerState.squareCatalogChild(
          SquareCatalogId(yearHistory.square.toID),
          yearHistory
        )
      }
    }

  private def makeMonthParent(history: CrawlerState, monthHistory: History.MonthHistory) =
    if (history.months.contains(monthHistory)) {
      printInfo[F](s"month ${monthHistory.toID} already exists") *>
        Sync[F].pure(CrawlerState.initial)
    } else {
      val monthCatalog = StacCatalog(
        "1.0.0-beta1",
        Nil,
        s"catalog-month-${monthHistory.toID}",
        None,
        s"Catalog holding descendants of month ${monthHistory.month}",
        List(
          StacLink("../catalog.json", StacLinkType.Parent, Some(`application/json`), None),
          StacLink("../../../../../collection.json", StacLinkType.StacRoot, Some(`application/json`), None)
        )
      )
      printInfo[F](s"month ${monthHistory.toID} did not exist!") map { _ =>
        CrawlerState.monthHistory(monthHistory, monthCatalog) `combine` CrawlerState.yearCatalogChild(
          YearCatalogId(monthHistory.year.toID),
          monthHistory
        )
      }
    }

  private def makeDayParent(history: CrawlerState, dayHistory: History.DayHistory) =
    if (history.days.contains(dayHistory)) {
      printInfo[F](s"day ${dayHistory.toID} already exists") *>
        Sync[F].pure(CrawlerState.initial)
    } else {
      val dayCatalog = StacCatalog(
        "1.0.0-beta1",
        Nil,
        s"catalog-day-${dayHistory.toID}",
        None,
        s"Catalog holding descendants of day ${dayHistory.day}",
        List(
          StacLink("../catalog.json", StacLinkType.Parent, Some(`application/json`), None),
          StacLink("../../../../../../collection.json", StacLinkType.StacRoot, Some(`application/json`), None)
        )
      )
      printInfo[F](s"day ${dayHistory.toID} did not exist!") map { _ =>
        CrawlerState.dayHistory(dayHistory, dayCatalog) `combine` CrawlerState.monthCatalogChild(
          MonthCatalogId(dayHistory.month.toID),
          dayHistory
        )
      }
    }

  private def getItemBandAssets(tileInfo: TileInfo): Map[String, StacItemAsset] = {
    Map(
      s2Bands
        .map({ (band: Band) =>
          band.name.value -> StacItemAsset(
            s"s3://${l1cBucket}/${tileInfo.path}/${band.name}.jp2",
            band.commonName map { _.value },
            None,
            Set(StacAssetRole.Data),
            Some(`image/jp2`)
          ).addExtensionFields(
            EOAssetExtension(NonEmptyList.one(band))
          )
        })
        .toList: _*
    )
  }

  private def getMetadataItemAssets(tileInfo: TileInfo): Map[String, StacItemAsset] = {
    Map(
      "info" -> StacItemAsset(
        s"s3://${l1cBucket}/${tileInfo.path}/tileInfo.json",
        Some("Tile Info JSON"),
        None,
        Set(StacAssetRole.Metadata),
        Some(`application/json`)
      ),
      "thumbnail" -> StacItemAsset(
        s"s3://${l1cBucket}/${tileInfo.path}/preview.jpg",
        None,
        None,
        Set(StacAssetRole.Thumbnail),
        Some(`image/jpeg`)
      ),
      "xml-metadata" -> StacItemAsset(
        s"s3://${l1cBucket}/${tileInfo.path}/metadata.xml",
        None,
        None,
        Set(StacAssetRole.Metadata),
        Some(`application/xml`)
      ),
      "tki" -> StacItemAsset(
        s"s3://${l1cBucket}/${tileInfo.path}/TKI.jp2",
        Some("True color image"),
        None,
        Set(StacAssetRole.Overview),
        Some(`image/jp2`)
      ).addExtensionFields(EOAssetExtension(trueColorBands))
    )
  }

  private def makeItem(productInfo: ProductInfo, tileInfo: TileInfo): StacItem = {
    val reprojectedDataGeom = tileInfo.tileDataGeometry.geom.reproject(tileInfo.tileDataGeometry.crs, LatLng)
    val dataExtent          = reprojectedDataGeom.extent
    val eoExt               = EOItemExtension(s2Bands, Some(tileInfo.cloudyPixelPercentage.value))
    StacItem(
      s"${productInfo.id.value}",
      "1.0.0-beta1",
      List("eo"),
      geometry = reprojectedDataGeom,
      bbox = TwoDimBbox(dataExtent.xmin, dataExtent.ymin, dataExtent.xmax, dataExtent.ymax),
      links = List(
        // the parent will always be in ../catalog.json, regardless of how we got here
        StacLink(
          "../catalog.json",
          StacLinkType.Parent,
          Some(`application/json`),
          None
        ),
        StacLink("../../../../../../../collection.json", StacLinkType.StacRoot, Some(`application/json`), None),
        StacLink("../../../../../../../collection.json", StacLinkType.Collection, Some(`application/json`), None)
      ),
      assets = getItemBandAssets(tileInfo) ++ getMetadataItemAssets(tileInfo),
      collection = Some("Sentinel-2 Scenes"),
      properties = Map(
        "datetime"               -> tileInfo.timestamp.asJson,
        "sentinel:sequence"      -> tileInfo.path.value.value.last.asJson,
        "sentinel:utm_zone"      -> tileInfo.utmZone.asJson,
        "sentinel:latitude_band" -> tileInfo.latitudeBand.asJson,
        "sentinel:grid_square"   -> tileInfo.gridSquare.asJson,
        "sentinel:data_path"     -> s"s3://${l1cBucket}/${tileInfo.path}".asJson
      ).asJsonObject
    ).addExtensionFields(eoExt)
  }

  private val timezone = ZoneId.of("UTC")

  private def getYear(instant: Instant): Int =
    ZonedDateTime.ofInstant(instant, timezone).getYear

  private def getMonth(instant: Instant): Int =
    ZonedDateTime.ofInstant(instant, timezone).getMonthValue

  private def getDay(instant: Instant): Int =
    ZonedDateTime.ofInstant(instant, timezone).getDayOfMonth
}
