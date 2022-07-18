package portals

import org.slf4j.{LoggerFactory => SLLoggerFactory, Logger => SLLogger}
import ch.qos.logback.classic.{LoggerContext => LBLoggerContext, Logger => LBLogger, Level => LBLevel}

type Logger = SLLogger

object Logger:
  setLevel("INFO") // set log level to INFO

  def apply(name: String): Logger =
    SLLoggerFactory.getLogger(name)

  def setLevel(level: String): Unit =
    val loggerContext: LBLoggerContext = SLLoggerFactory.getILoggerFactory().asInstanceOf[LBLoggerContext]
    val rootLogger: ch.qos.logback.classic.Logger = loggerContext.getLogger(SLLogger.ROOT_LOGGER_NAME)
    level match
      case "DEBUG" => rootLogger.setLevel(LBLevel.DEBUG)
      case "INFO"  => rootLogger.setLevel(LBLevel.INFO)
      case "WARN"  => rootLogger.setLevel(LBLevel.WARN)
      case "ERROR" => rootLogger.setLevel(LBLevel.ERROR)
      case "OFF"   => rootLogger.setLevel(LBLevel.OFF)
      case _       => throw new IllegalArgumentException(s"Unknown log level: $level")
