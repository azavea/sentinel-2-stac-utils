package com.azavea.s2stac.jsonio

import com.azavea.s2stac.datamodel.types.DataPath

import better.files.{File => ScalaFile}
import cats.effect.Sync
import cats.syntax.functor._
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3URI
import eu.timepit.refined.auto._
import io.circe.Encoder
import io.circe.syntax._

trait JsonWriter[F[_]] {
  def toPath[T: Encoder](path: DataPath, value: T): F[Unit]
}

class SyncJsonWriter[F[_]: Sync](s3Client: AmazonS3) extends JsonWriter[F] {

  private def toS3Path[T: Encoder](path: DataPath, value: T): F[Unit] = {
    val awsURI = new AmazonS3URI(path.value)
    val bucket = awsURI.getBucket
    val key    = awsURI.getKey
    Sync[F]
      .delay(
        s3Client.putObject(bucket, key, value.asJson.spaces2)
      ) map { _ =>
      ()
    }

  }

  private def toLocalPath[T: Encoder](path: DataPath, value: T): F[Unit] = {
    val outFile = ScalaFile(path.value)
    Sync[F].delay {
      outFile.createIfNotExists(false, true).append(value.asJson.spaces2)
    } map { _ =>
      ()
    }
  }

  def toPath[T: Encoder](path: DataPath, value: T): F[Unit] =
    (if (path.value.startsWith("s3")) {
       toS3Path(path, value)
     } else {
       toLocalPath(path, value)
     })

}
