val scala3Version = "3.1.2"

// FIXME: do this in a better way, we require at least Java 9, or classVersion 53 for Flow
initialize := {
  val _ = initialize.value // run the previous initialization
  val classVersion = sys.props("java.class.version")
  val specVersion = sys.props("java.specification.version")
  assert(
    classVersion.toFloat.toInt >= 53, // 53 is Java 9
    "Java 9 or above required. Current Java version: " + specVersion + ", " + classVersion + ".",
  )
}

lazy val root = project
  .in(file("."))
  .settings(
    name := "workflows",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
    )
  )