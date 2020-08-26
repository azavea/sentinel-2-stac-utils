package com.azavea.s2stac.jsonio

import com.azavea.s2stac.datamodel.types.DataPath
import com.azavea.s2stac.{printError, printWarn}

import cats.ApplicativeError
import cats.effect.Sync
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.functor._
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.{AmazonS3, AmazonS3URI}
import eu.timepit.refined.auto._
import io.chrisdavenport.log4cats.Logger
import io.circe.parser.decode
import io.circe.{CursorOp, Decoder, DecodingFailure, Error, ParsingFailure}
import retry.RetryDetails.{GivingUp, WillDelayAndRetry}
import retry.{RetryDetails, RetryPolicy, Sleep, retryingOnSomeErrors}

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

sealed trait JsonReadError extends Throwable {
  def toMessage: String
}

case class AmazonS3ReadError(path: DataPath, message: String) extends JsonReadError {
  def toMessage: String = s"""
    | When I tried to read data from $path, Amazon S3 reported an error.
    |
    | Here's what it said:
    |
    |     $message
    """.trim.stripMargin
}

case class CirceError(path: DataPath, className: String, underlying: Error) extends JsonReadError {

  def toMessage: String = underlying match {
    case ParsingFailure(message, _) =>
      s"""
        | I tried to read JSON from $path, but I found malformed JSON.
        |
        | Here's the problem I ran into:
        |
        |     $message
        """
    case DecodingFailure(message, history) =>
      s"""
        | I tried to read a value of type $className from $path.
        |
        | I didn't find the JSON I expected.
        |
        | At ${CursorOp.opsToPath(history)}, here's the problem I had:
        |
        |     $message
        """.trim.stripMargin
  }
}

trait JsonReader[F[_]] {
  def fromPath[T: Decoder: ClassTag](path: DataPath): F[Either[JsonReadError, T]]
}

class SyncJsonReader[F[_]: Sync: Sleep](s3Client: AmazonS3, retryPolicy: RetryPolicy[F])(implicit logger: Logger[F])
    extends JsonReader[F] {

  private def fromS3Path(path: DataPath): F[String] = {
    val awsURI = new AmazonS3URI(path.value)
    retryingOnSomeErrors[String](
      policy = retryPolicy,
      isWorthRetrying = (err: Throwable) =>
        err match {
          case CirceError(_, _, _) => false
          case _                   => true
      },
      onError = (_: Throwable, details: RetryDetails) => {
        details match {

          case WillDelayAndRetry(nextDelay: FiniteDuration, retriesSoFar: Int, _: FiniteDuration) =>
            printWarn(
              s"Failed to read from $path. So far I have retried $retriesSoFar times. Next attempt after ${nextDelay.toSeconds} seconds."
            )

          case GivingUp(totalRetries: Int, totalDelay: FiniteDuration) =>
            printError(s"Giving up for $path after $totalRetries retries and ${totalDelay.toSeconds} seconds.") *> ApplicativeError[
              F,
              Throwable
            ].raiseError[Unit](
              AmazonS3ReadError(path, s"Failed to read after $totalRetries attempts")
            )

        }
      }
    )(
      Sync[F]
        .delay(s3Client.getObject(new GetObjectRequest(awsURI.getBucket, awsURI.getKey, true)).getObjectContent)
        .map(
          is => scala.io.Source.fromInputStream(is).mkString
        )
    )
  }

  private def fromLocalPath(path: DataPath): F[String] =
    Sync[F].delay {
      scala.io.Source.fromFile(path.value).getLines.mkString
    }

  def fromPath[T: Decoder: ClassTag](path: DataPath): F[Either[JsonReadError, T]] =
    (if (path.value.startsWith("s3")) {
       fromS3Path(path)
     } else {
       fromLocalPath(path)
     }).attempt map {
      case Left(e) => Either.left(AmazonS3ReadError(path, e.getMessage))
      case Right(s) =>
        decode[T](s).leftMap { err =>
          CirceError(path, implicitly[ClassTag[T]].runtimeClass.getSimpleName, err)
        }
    }
}
