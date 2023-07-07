lazy val scala3Version = "3.3.0"
lazy val junitInterfaceVersion = "0.11"
lazy val logbackversion = "1.4.8"
lazy val akkaVersion = "2.6.20"
lazy val pprintversion = "0.8.1"
lazy val nexmarkVersion = "2.48.0"
lazy val caskVersion = "0.9.1"
lazy val upickleVersion = "3.1.0"
lazy val requestsVersion = "0.8.0"
lazy val mainargsVersion = "0.5.0"
lazy val scalajsstubsVersion = "1.1.0"
lazy val scalajsdomVersion = "2.6.0"

ThisBuild / organization := "org.portals-project"
ThisBuild / organizationName := "Portals Project"
ThisBuild / organizationHomepage := Some(url("https://portals-project.org/"))

ThisBuild / description := "Portals"
ThisBuild / licenses := List("Apache-2.0" -> new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("https://github.com/portals-project/portals"))

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := scala3Version

lazy val portals = crossProject(JSPlatform, JVMPlatform)
  .in(file("portals-core"))
  .settings(
    name := "portals-core",
    Compile / doc / target := target.value / "api",
    libraryDependencies += "com.lihaoyi" %%% "pprint" % pprintversion,
    libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    libraryDependencies += "org.scala-js" %% "scalajs-stubs" % scalajsstubsVersion % "provided",
  )
  .jvmSettings(
    libraryDependencies += "ch.qos.logback" % "logback-classic" % logbackversion,
    libraryDependencies += "com.novocode" % "junit-interface" % junitInterfaceVersion % "test",
  )
  .jsSettings()

lazy val benchmark = project
  .in(file("portals-benchmark"))
  .settings(
    name := "portals-benchmark",
    resolvers += "confluent" at "https://packages.confluent.io/maven/", // NEXMark benchmark
    libraryDependencies += "org.apache.beam" % "beam-sdks-java-nexmark" % nexmarkVersion, // NEXMark benchmark
    libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  )
  .dependsOn(portals.jvm % "test->test;compile->compile")

lazy val examples = project
  .in(file("portals-examples"))
  .settings(
    name := "portals-examples",
    libraryDependencies += "com.novocode" % "junit-interface" % junitInterfaceVersion % "test",
  )
  .dependsOn(portals.jvm % "test->test;compile->compile")

lazy val distributed = project
  .in(file("portals-distributed"))
  .settings(
    name := "portals-distributed",
    libraryDependencies += "com.lihaoyi" %% "cask" % caskVersion,
    libraryDependencies += "com.lihaoyi" %% "upickle" % upickleVersion,
    libraryDependencies += "com.lihaoyi" %% "requests" % requestsVersion,
    libraryDependencies += "com.lihaoyi" %% "mainargs" % mainargsVersion,
  )
  .dependsOn(portals.jvm % "test->test;compile->compile")
  .dependsOn(examples % "test->test;compile->compile")

lazy val portalsjs = crossProject(JSPlatform)
  .in(file("portals-portalsjs"))
  .settings(
    name := "portals-portalsjs",
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % scalajsdomVersion,
    Compile / scalaJSLinkerConfig ~= { _.withSourceMap(false) },
  )
  .dependsOn(portals % "test->test;compile->compile")
