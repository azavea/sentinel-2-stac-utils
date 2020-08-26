package com.azavea.s2stac.csvio

import com.azavea.s2stac.datamodel.types.DataPath
import com.azavea.s2stac.{printError, printWarn}

import cats.ApplicativeError
import cats.effect.Sync
import cats.syntax.apply._
import cats.syntax.functor._
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.{AmazonS3, AmazonS3URI}
import eu.timepit.refined.auto._
import io.chrisdavenport.log4cats.Logger
import kantan.csv.ops._
import kantan.csv.{ReadResult, RowDecoder, rfc}
import retry.RetryDetails.{GivingUp, WillDelayAndRetry}
import retry.{RetryDetails, RetryPolicy, Sleep, retryingOnAllErrors}

trait CsvReader[F[_]] {
  def fromPath[T: RowDecoder](path: DataPath): F[List[ReadResult[T]]]
}

class SyncCsvReader[F[_]: Sync: Sleep](s3Client: AmazonS3, retryPolicy: RetryPolicy[F])(implicit logger: Logger[F])
    extends CsvReader[F] {

  private def fromLocalPath[T: RowDecoder](path: DataPath): F[List[ReadResult[T]]] = {
    val source = scala.io.Source.fromFile(path.value).mkString
    Sync[F].delay(source.readCsv[List, T](rfc.withoutHeader))
  }

  private def fromS3Path[T: RowDecoder](path: DataPath): F[List[ReadResult[T]]] = {
    val awsURI = new AmazonS3URI(path.value)
    retryingOnAllErrors[List[ReadResult[T]]](
      policy = retryPolicy,
      onError = (e: Throwable, details: RetryDetails) => {
        details match {

          case WillDelayAndRetry(nextDelay, retriesSoFar, _) =>
            printWarn(
              s"Failed to read a CSV from $path. So far I have retried $retriesSoFar times. Next attempt after ${nextDelay.toSeconds} seconds."
            )

          case GivingUp(totalRetries, totalDelay) =>
            printError(
              s"Giving up reading a CSV from $path after $totalRetries retries and ${totalDelay.toSeconds} seconds."
            ) *> ApplicativeError[
              F,
              Throwable
            ].raiseError[Unit](
              e
            )

        }
      }
    )(
      Sync[F]
        .delay(s3Client.getObject(new GetObjectRequest(awsURI.getBucket, awsURI.getKey, true)).getObjectContent) map {
        is =>
          is.readCsv[List, T](rfc.withoutHeader)
      }
    )
  }

  def fromPath[T: RowDecoder](path: DataPath): F[List[ReadResult[T]]] =
    if (path.value.startsWith("s3")) {
      fromS3Path[T](path)
    } else {
      fromLocalPath(path)
    }
}
