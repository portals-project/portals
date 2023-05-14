package portals.util

import com.typesafe.config.{Config, ConfigFactory}

object Config {
  /**
   * Load the application configuration file.
   * Wrapper class in order to have the config loaded only once.
   */
  private val config: Config = ConfigFactory.load("portals.conf")

  def getString(key: String): String = {
    config.getString(key)
  }
}
