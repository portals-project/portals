package portals.util

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

object Config {
  /**
   * Load the application configuration file.
   * Wrapper class in order to have the config loaded only once.
   */
  private var config: Config = ConfigFactory.load("portals.conf")

  def getString(key: String): String = {
    config.getString(key)
  }

  /**
   * Set a value in the configuration file.
   * @param key The key to set.
   * @param value The value to set.
   * @example setString("state.backend", "MapStateBackendImpl")
   */
  def setString(key: String, value: String): Unit = {
    val configValue = ConfigValueFactory.fromAnyRef(value)
    config = config.withValue(key, configValue)
  }
}
