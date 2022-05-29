package pods.workflows

import java.util.logging.{Logger => JLogger}

type Logger = JLogger

object Logger:
  def apply(name: String): Logger = JLogger.getLogger(name)
