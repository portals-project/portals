package pods.workflows

import org.slf4j.LoggerFactory

type Logger = org.slf4j.Logger

object Logger:
  def apply(name: String): Logger = 
    LoggerFactory.getLogger(name)
