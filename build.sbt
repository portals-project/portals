val scala3Version = "3.2.0"
val junitInterfaceVersion = "0.11"
val logbackversion = "1.2.11"
val akkaVersion = "2.6.20"

lazy val root = project
  .in(file("."))
  .settings(
    name := "workflows",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    resolvers += "confluent" at "https://packages.confluent.io/maven/", // NEXMark benchmark
    libraryDependencies ++= Seq(
      "com.novocode" % "junit-interface" % junitInterfaceVersion % "test",
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "com.lihaoyi" %% "pprint" % "0.7.0",
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "org.apache.beam" % "beam-sdks-java-nexmark" % "2.41.0", // NEXMark benchmark
    )
  )
