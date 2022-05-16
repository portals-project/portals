val scala3Version = "3.1.2"

lazy val root = project
  .in(file("."))
  .settings(
    name := "workflows",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % "1.7.36",
      "org.slf4j" % "slf4j-simple" % "1.7.36"
    )
  )

