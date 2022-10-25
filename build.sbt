lazy val scala3Version = "3.2.0"
lazy val junitInterfaceVersion = "0.11"
lazy val logbackversion = "1.2.11"
lazy val akkaVersion = "2.6.20"
lazy val pprintversion = "0.7.0"
lazy val nexmarkVersion = "2.41.0"

ThisBuild / organization := "org.portals-project"
ThisBuild / organizationName := "Portals-Project"
ThisBuild / organizationHomepage := Some(url("https://portals-project.org/"))

ThisBuild / description := "Portals"
ThisBuild / licenses := List("Apache-2.0" -> new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("https://github.com/portals-project/portals"))

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := scala3Version

lazy val portals = project
  .in(file("core"))
  .settings(
    name := "portals",
    libraryDependencies += "com.novocode" % "junit-interface" % junitInterfaceVersion % "test",
    libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    libraryDependencies += "ch.qos.logback" % "logback-classic" % logbackversion,
    libraryDependencies += "com.lihaoyi" %% "pprint" % pprintversion,
  )

lazy val benchmarks = project
  .in(file("benchmark"))
  .settings(
    name := "portals-benchmark",
    resolvers += "confluent" at "https://packages.confluent.io/maven/", // NEXMark benchmark
    libraryDependencies += "org.apache.beam" % "beam-sdks-java-nexmark" % nexmarkVersion, // NEXMark benchmark
    libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  )
  .dependsOn(portals)
