// tag::sbt[]
ThisBuild / scalaVersion := "3.3.8"

lazy val micronautVersion =
  sys.props.getOrElse("micronaut.version", "5.0.1-SNAPSHOT")

lazy val micronautScalaVersion =
  sys.props.getOrElse("micronaut.scala.version", "1.0.0-SNAPSHOT")

libraryDependencies ++= Seq(
  "io.micronaut" % "micronaut-runtime" % micronautVersion,
  ("io.micronaut.scala" % "micronaut-inject-scala" % micronautScalaVersion cross CrossVersion.full) % Runtime
)

addCompilerPlugin(
  "io.micronaut.scala" % "micronaut-inject-scala" % micronautScalaVersion cross CrossVersion.full
)

scalacOptions ++= Seq("-release:25", "-Yexplicit-nulls")
// end::sbt[]
