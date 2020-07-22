package com.azavea.s2stac.datamodel

import com.azavea.s2stac.datamodel.types._

import eu.timepit.refined.types.string.NonEmptyString

trait History {

  def combine(sep: Char): NonEmptyString
  def toID: NonEmptyString                      = combine('-')
  def toPath(from: OutputCatalogRoot): DataPath = DataPath(NonEmptyString.unsafeFrom(s"${from.value}/${combine('/')}"))
}

object History {

  case class BandHistory(zone: UtmZone, band: UtmLatitudeBand) extends History {
    def combine(sep: Char): NonEmptyString = NonEmptyString.unsafeFrom(s"$zone$sep$band")
  }

  case class GridSquareHistory(band: BandHistory, square: String) extends History {
    def combine(sep: Char): NonEmptyString = NonEmptyString.unsafeFrom(s"${band.combine(sep)}$sep$square")
  }

  case class YearHistory(square: GridSquareHistory, year: Int) extends History {
    def combine(sep: Char): NonEmptyString = NonEmptyString.unsafeFrom(s"${square.combine(sep)}$sep$year")
  }

  case class MonthHistory(year: YearHistory, month: Int) extends History {
    def combine(sep: Char): NonEmptyString = NonEmptyString.unsafeFrom(f"${year.combine(sep)}$sep${month}%02d")
  }

  case class DayHistory(month: MonthHistory, day: Int) extends History {
    def combine(sep: Char): NonEmptyString = NonEmptyString.unsafeFrom(f"${month.combine(sep)}$sep${day}%02d")
  }

  case class DataSequenceHistory(day: DayHistory, sequence: Int) extends History {
    def combine(sep: Char): NonEmptyString = NonEmptyString.unsafeFrom(s"${day.combine(sep)}$sep$sequence")
  }
}
