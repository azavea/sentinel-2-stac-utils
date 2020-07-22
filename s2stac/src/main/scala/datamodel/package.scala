package com.azavea.s2stac

import com.azavea.stac4s.StacCatalog

import cats.Semigroup
import io.circe.{Decoder, Encoder}
import io.estatico.newtype.Coercible

package object datamodel {
  implicit def coercibleEncoder[R, N](implicit ev: Coercible[Encoder[R], Encoder[N]], R: Encoder[R]): Encoder[N] = ev(R)
  implicit def coercibleDecoder[R, N](implicit ev: Coercible[Decoder[R], Decoder[N]], R: Decoder[R]): Decoder[N] = ev(R)

  implicit val catalogIdSemigroup: Semigroup[StacCatalog] = new Semigroup[StacCatalog] {
    def combine(x: StacCatalog, y: StacCatalog): StacCatalog = x
  }

}
