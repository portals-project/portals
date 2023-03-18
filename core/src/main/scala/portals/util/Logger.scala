package portals.util

import scala.scalajs.js.annotation.JSExport

// import org.slf4j.{Logger => SLLogger}
// import org.slf4j.{LoggerFactory => SLLoggerFactory}

// import ch.qos.logback.classic.{Level => LBLevel}
// import ch.qos.logback.classic.{Logger => LBLogger}
// import ch.qos.logback.classic.{LoggerContext => LBLoggerContext}

// type Logger = SLLogger
trait Logger:
  val name: String
  def log = this
  @JSExport
  def info(msg: Any): Unit =
    val _msg = Logger._level + name + " - " + msg.toString()
    println(_msg)

object Logger:
  setLevel("INFO") // set log level to INFO
  var _level = ""

  def apply(_name: String): Logger =
    new Logger { val name = _name }
    // SLLoggerFactory.getLogger(name)

  def setLevel(level: String): Unit = _level = level
  // val loggerContext: LBLoggerContext = SLLoggerFactory.getILoggerFactory().asInstanceOf[LBLoggerContext]
  // val rootLogger: ch.qos.logback.classic.Logger = loggerContext.getLogger(SLLogger.ROOT_LOGGER_NAME)
  // level match
  //   case "DEBUG" => rootLogger.setLevel(LBLevel.DEBUG)
  //   case "INFO" => rootLogger.setLevel(LBLevel.INFO)
  //   case "WARN" => rootLogger.setLevel(LBLevel.WARN)
  //   case "ERROR" => rootLogger.setLevel(LBLevel.ERROR)
  //   case "OFF" => rootLogger.setLevel(LBLevel.OFF)
  //   case _ => throw new IllegalArgumentException(s"Unknown log level: $level")
