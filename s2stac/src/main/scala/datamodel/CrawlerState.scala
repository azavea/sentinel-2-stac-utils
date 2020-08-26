package com.azavea.s2stac.datamodel

import com.azavea.s2stac.datamodel.types._
import com.azavea.stac4s.StacCatalog

import cats.Monoid
import cats.implicits._
import monocle.Lens
import monocle.macros.GenLens

final case class CrawlerState(
    remaining: List[InventoryCsvRow],
    utmZones: Map[UtmZone, StacCatalog],
    utmBands: Map[History.BandHistory, StacCatalog],
    utmGridSquares: Map[History.GridSquareHistory, StacCatalog],
    years: Map[History.YearHistory, StacCatalog],
    months: Map[History.MonthHistory, StacCatalog],
    days: Map[History.DayHistory, StacCatalog],
    dataSequences: Set[History.DataSequenceHistory],
    zoneChildLinks: Map[ZoneCatalogId, Set[History.BandHistory]],
    bandChildLinks: Map[BandCatalogId, Set[History.GridSquareHistory]],
    squareChildLinks: Map[SquareCatalogId, Set[History.YearHistory]],
    yearChildLinks: Map[YearCatalogId, Set[History.MonthHistory]],
    monthChildLinks: Map[MonthCatalogId, Set[History.DayHistory]],
    dayChildLinks: Map[DayCatalogId, Set[ItemId]],
    errorPaths: Set[DataPath]
)

object CrawlerState {

  implicit val crawlerStateMonoid: Monoid[CrawlerState] = new Monoid[CrawlerState] {
    def empty: CrawlerState = initial

    def combine(x: CrawlerState, y: CrawlerState): CrawlerState = CrawlerState(
      x.remaining `combine` y.remaining,
      x.utmZones `combine` y.utmZones,
      x.utmBands `combine` y.utmBands,
      x.utmGridSquares `combine` y.utmGridSquares,
      x.years `combine` y.years,
      x.months `combine` y.months,
      x.days `combine` y.days,
      x.dataSequences `combine` y.dataSequences,
      x.zoneChildLinks `combine` y.zoneChildLinks,
      x.bandChildLinks `combine` y.bandChildLinks,
      x.squareChildLinks `combine` y.squareChildLinks,
      x.yearChildLinks `combine` y.yearChildLinks,
      x.monthChildLinks `combine` y.monthChildLinks,
      x.dayChildLinks `combine` y.dayChildLinks,
      x.errorPaths `combine` y.errorPaths
    )
  }

  private val remainingLens: Lens[CrawlerState, List[InventoryCsvRow]] = GenLens[CrawlerState](_.remaining)

  def initial: CrawlerState = CrawlerState(
    Nil,
    Map.empty,
    Map.empty,
    Map.empty,
    Map.empty,
    Map.empty,
    Map.empty,
    Set.empty,
    Map.empty,
    Map.empty,
    Map.empty,
    Map.empty,
    Map.empty,
    Map.empty,
    Set.empty
  )

  def dataSequenceHistory(dataSequenceHistory: History.DataSequenceHistory) = initial.copy(
    dataSequences = Set(dataSequenceHistory)
  )

  def dayHistory(dayHistory: History.DayHistory, catalog: StacCatalog) = initial.copy(
    days = Map(dayHistory -> catalog)
  )

  def monthHistory(monthHistory: History.MonthHistory, catalog: StacCatalog) = initial.copy(
    months = Map(monthHistory -> catalog)
  )

  def yearHistory(yearHistory: History.YearHistory, catalog: StacCatalog) = initial.copy(
    years = Map(yearHistory -> catalog)
  )

  def gridSquareHistory(gridSquareHistory: History.GridSquareHistory, catalog: StacCatalog) = initial.copy(
    utmGridSquares = Map(gridSquareHistory -> catalog)
  )

  def bandHistory(bandHistory: History.BandHistory, catalog: StacCatalog) = initial.copy(
    utmBands = Map(bandHistory -> catalog)
  )

  def zoneHistory(zone: UtmZone, catalog: StacCatalog) = initial.copy(
    utmZones = Map(zone -> catalog)
  )

  def dayCatalogChild(catalogId: DayCatalogId, itemId: ItemId) = initial.copy(
    dayChildLinks = Map(catalogId -> Set(itemId))
  )

  def monthCatalogChild(monthCatalogId: MonthCatalogId, dayHistory: History.DayHistory) = initial.copy(
    monthChildLinks = Map(monthCatalogId -> Set(dayHistory))
  )

  def yearCatalogChild(yearCatalogId: YearCatalogId, monthHistory: History.MonthHistory) = initial.copy(
    yearChildLinks = Map(yearCatalogId -> Set(monthHistory))
  )

  def squareCatalogChild(squareCatalogId: SquareCatalogId, yearHistory: History.YearHistory) = initial.copy(
    squareChildLinks = Map(squareCatalogId -> Set(yearHistory))
  )

  def bandCatalogChild(bandCatalogId: BandCatalogId, squareHistory: History.GridSquareHistory) = initial.copy(
    bandChildLinks = Map(bandCatalogId -> Set(squareHistory))
  )

  def zoneCatalogChild(zoneCatalogId: ZoneCatalogId, bandHistory: History.BandHistory) = initial.copy(
    zoneChildLinks = Map(zoneCatalogId -> Set(bandHistory))
  )

  def withRemaining(rows: List[InventoryCsvRow]): CrawlerState => CrawlerState = remainingLens.modify(_ => rows)

  def withErrorPath(path: DataPath): CrawlerState = initial.copy(errorPaths = Set(path))

}
