package com.azavea.s2stac.csvio

import com.azavea.s2stac.datamodel.types.DataPath

import cats.effect.Sync
import cats.syntax.functor._
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.{AmazonS3, AmazonS3URI}
import eu.timepit.refined.auto._
import kantan.csv.{rfc, ReadResult, RowDecoder}
import kantan.csv.ops._

trait CsvReader[F[_]] {
  def fromPath[T: RowDecoder](path: DataPath): F[List[ReadResult[T]]]
}

class SyncCsvReader[F[_]: Sync](s3Client: AmazonS3) extends CsvReader[F] {

  private def fromLocalPath[T: RowDecoder](path: DataPath): F[List[ReadResult[T]]] = {
    val source = scala.io.Source.fromFile(path.value).mkString
    Sync[F].delay(source.readCsv[List, T](rfc.withoutHeader))
  }

  private def fromS3Path[T: RowDecoder](path: DataPath): F[List[ReadResult[T]]] = {
    val awsURI = new AmazonS3URI(path.value)
    val inputStreamIO =
      Sync[F].delay(s3Client.getObject(new GetObjectRequest(awsURI.getBucket, awsURI.getKey, true)).getObjectContent)
    inputStreamIO map { is =>
      is.readCsv[List, T](rfc.withoutHeader)
    }
  }

  def fromPath[T: RowDecoder](path: DataPath): F[List[ReadResult[T]]] =
    if (path.value.startsWith("s3")) {
      fromS3Path[T](path)
    } else {
      fromLocalPath(path)
    }
}
