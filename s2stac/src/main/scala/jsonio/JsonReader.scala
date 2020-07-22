package com.azavea.s2stac.jsonio

import com.azavea.s2stac.datamodel.types.DataPath

import cats.effect.Sync
import cats.syntax.functor._
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.{AmazonS3, AmazonS3URI}
import eu.timepit.refined.auto._
import io.circe.parser.decode
import io.circe.{Decoder, Error}

trait JsonReader[F[_]] {
  def fromPath[T: Decoder](path: DataPath): F[Either[Error, T]]
}

class SyncJsonReader[F[_]: Sync](s3Client: AmazonS3) extends JsonReader[F] {

  private def fromS3Path(path: DataPath): F[String] = {
    val awsURI = new AmazonS3URI(path.value)
    val inputStreamIO =
      Sync[F].delay(s3Client.getObject(new GetObjectRequest(awsURI.getBucket, awsURI.getKey, true)).getObjectContent)
    inputStreamIO.map(is => scala.io.Source.fromInputStream(is).mkString)
  }

  private def fromLocalPath(path: DataPath): F[String] =
    Sync[F].delay {
      scala.io.Source.fromFile(path.value).getLines.mkString
    }

  def fromPath[T: Decoder](path: DataPath): F[Either[Error, T]] =
    (if (path.value.startsWith("s3")) {
       fromS3Path(path)
     } else {
       fromLocalPath(path)
     }) map { s =>
      decode[T](s)
    }
}
