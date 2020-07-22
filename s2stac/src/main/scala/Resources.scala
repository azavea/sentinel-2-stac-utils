package com.azavea.s2stac

import cats.effect.{Resource, Sync}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}

object Resources {

  def s3[F[_]: Sync]: Resource[F, AmazonS3] = Resource.liftF {
    Sync[F].delay(
      AmazonS3ClientBuilder
        .standard()
        .withForceGlobalBucketAccessEnabled(true)
        .build()
    )
  }
}
