package com.azavea.s2stac.datamodel

import cats.implicits._
import geotrellis.proj4.CRS
import geotrellis.proj4.LatLng
import geotrellis.vector.Geometry
import geotrellis.vector.io.json.CrsFormats._
import geotrellis.vector.io.json.GeometryFormats._
import geotrellis.vector.io.json.NamedCRS
import geotrellis.vector.reproject.Implicits._
import io.circe.CursorOp.DownField
import io.circe.syntax._
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import monocle.macros.GenLens

final case class GeoJSONWithCRS[T <: Geometry: Decoder: Encoder](
    crs: CRS,
    geom: T
)

object GeoJSONWithCRS {

  private val nameLens = GenLens[NamedCRS](_.name)

  implicit def decGeoJSONWithCRS[T <: Geometry: Decoder: Encoder]: Decoder[GeoJSONWithCRS[T]] = { (c: HCursor) =>
    (
      c.downField("crs").as[NamedCRS] map { namedCrs =>
        // because we can't get a CRS from the code as written in the S2 metadata,
        // assume that the last bit is a valid EPSG code that we _do_
        // know how to work with
        nameLens.modify(name => s"EPSG:${name.split(":").last}")(namedCrs)
      } flatMap { trimmedCrs =>
        Either.fromOption(
          trimmedCrs.toCRS,
          DecodingFailure(s"Could not get a CRS from ${trimmedCrs.name}", List(DownField("crs")))
        )
      },
      c.value.as[T]
    ).mapN(GeoJSONWithCRS[T])
  }

  implicit def encGeoJSONWithCRS[T <: Geometry: Decoder: Encoder]: Encoder[GeoJSONWithCRS[T]] =
    new Encoder[GeoJSONWithCRS[T]] {
      // we break the promise of round-trip serialization, since we don't know for certain that the
      // CRS can be encoded, but we want a useful geometry at the end regardless. Instead of worrying
      // about the CRS (which the geojson spec dropped anyway), just reproject to LatLng
      def apply(g: GeoJSONWithCRS[T]): Json = g.geom.reproject(g.crs, LatLng).asJson
    }
}
