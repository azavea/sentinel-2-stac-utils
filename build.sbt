// give the user a nice default project!
ThisBuild / organization := "com.azavea"
ThisBuild / scalaVersion := "2.12.12"

val AWSVersion         = "1.11.751"
val BetterFilesVersion = "3.9.1"
val CirceVersion       = "0.13.0"
val DeclineVersion     = "1.2.0"
val EmojiVersion       = "1.2.1"
val FansiVersion       = "0.2.7"
val GeoTrellisVersion  = "3.4.1"
val MonocleVersion     = "2.0.4"
val NewtypeVersion     = "0.4.4"
val RefinedVersion     = "0.9.14"
val Stac4sVersion      = "0.0.12"
val SttpVersion        = "2.2.1"

val s2StacDependencies = List(
  "com.amazonaws"                % "aws-java-sdk-core"               % AWSVersion,
  "com.amazonaws"                % "aws-java-sdk-s3"                 % AWSVersion,
  "com.azavea.stac4s"            %% "core"                           % Stac4sVersion,
  "com.github.julien-truffaut"   %% "monocle-core"                   % MonocleVersion,
  "com.github.julien-truffaut"   %% "monocle-macro"                  % MonocleVersion,
  "com.github.pathikrit"         %% "better-files"                   % BetterFilesVersion,
  "com.lightbend"                %% "emoji"                          % EmojiVersion,
  "com.lihaoyi"                  %% "fansi"                          % FansiVersion,
  "com.monovore"                 %% "decline-effect"                 % DeclineVersion,
  "com.monovore"                 %% "decline-refined"                % DeclineVersion,
  "com.monovore"                 %% "decline"                        % DeclineVersion,
  "com.softwaremill.sttp.client" %% "async-http-client-backend-cats" % SttpVersion,
  "com.softwaremill.sttp.client" %% "circe"                          % SttpVersion,
  "com.softwaremill.sttp.client" %% "core"                           % SttpVersion,
  "eu.timepit"                   %% "refined"                        % RefinedVersion,
  "io.circe"                     %% "circe-generic"                  % CirceVersion,
  "io.circe"                     %% "circe-refined"                  % CirceVersion,
  "io.estatico"                  %% "newtype"                        % NewtypeVersion,
  "org.locationtech.geotrellis"  %% "geotrellis-vector"              % GeoTrellisVersion
)

lazy val s2stac = (project in file("./s2stac"))
  .settings(
    libraryDependencies ++= s2StacDependencies,
    externalResolvers ++= Seq(
      DefaultMavenRepository,
      Resolver.sonatypeRepo("snapshots"),
      Resolver.bintrayRepo("azavea", "maven"),
      Resolver.bintrayRepo("azavea", "geotrellis"),
      Resolver.typesafeIvyRepo("releases"),
      "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
      "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots",
      Resolver.file("local", file(Path.userHome.absolutePath + "/.ivy2/local"))(
        Resolver.ivyStylePatterns
      )
    ),
    assemblyMergeStrategy in assembly := {
      case "reference.conf"                       => MergeStrategy.concat
      case "application.conf"                     => MergeStrategy.concat
      case n if n.startsWith("META-INF/services") => MergeStrategy.concat
      case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") =>
        MergeStrategy.discard
      case "META-INF/MANIFEST.MF" => MergeStrategy.discard
      case _                      => MergeStrategy.first
    },
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
    ),
    addCompilerPlugin(
      scalafixSemanticdb
    ),
    ThisBuild / scalacOptions += "-Yrangepos",
    ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.4.0"
  )
