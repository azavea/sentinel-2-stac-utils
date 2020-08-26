package com.azavea.s2stac.jsonio

import com.azavea.s2stac.datamodel.types.DataPath
import com.azavea.s2stac.{printError, printWarn}

import better.files.{File => ScalaFile}
import cats.ApplicativeError
import cats.effect.Sync
import cats.syntax.apply._
import cats.syntax.functor._
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3URI
import eu.timepit.refined.auto._
import io.chrisdavenport.log4cats.Logger
import io.circe.Encoder
import io.circe.syntax._
import retry.RetryDetails.{GivingUp, WillDelayAndRetry}
import retry.{RetryDetails, RetryPolicy, Sleep, retryingOnAllErrors}

case class AmazonS3WriteError(path: DataPath, message: String) extends Throwable

trait JsonWriter[F[_]] {
  def toPath[T: Encoder](path: DataPath, value: T): F[Unit]
}

class SyncJsonWriter[F[_]: Sync: Sleep](s3Client: AmazonS3, retryPolicy: RetryPolicy[F])(implicit logger: Logger[F])
    extends JsonWriter[F] {

  private def toS3Path[T: Encoder](path: DataPath, value: T): F[Unit] = {
    val awsURI = new AmazonS3URI(path.value)
    val bucket = awsURI.getBucket
    val key    = awsURI.getKey
    retryingOnAllErrors[Unit](
      policy = retryPolicy,
      onError = (_: Throwable, details: RetryDetails) => {
        details match {
          case WillDelayAndRetry(nextDelay, retriesSoFar, _) =>
            printWarn(
              s"Failed to write to $path. So far I have retried $retriesSoFar times. Next attempt after ${nextDelay.toSeconds} seconds."
            )

          case GivingUp(totalRetries, totalDelay) =>
            printError(s"""" +
              | Giving up trying to write $path after $totalRetries retries and ${totalDelay.toSeconds} seconds.
              | 
              | Here's what I wanted to write in case you'd like to retry manually:
              |
              | ${value.asJson.spaces2}
              """.trim.stripMargin) *> ApplicativeError[
              F,
              Throwable
            ].raiseError[Unit](
              AmazonS3ReadError(path, s"Failed to read after $totalRetries attempts")
            )

        }
      }
    )(
      Sync[F]
        .delay(
          s3Client.putObject(bucket, key, value.asJson.spaces2)
        ) map { _ =>
        ()
      }
    )

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
