package portals.util

import portals.runtime.state.MapStateBackendImpl
import portals.runtime.state.RocksDBStateBackendImpl
import portals.runtime.state.StateBackend
import portals.util.Config

object StateBackendFactory {
  private val instance: StateBackend = createStateBackend()

  def getStateBackend(): StateBackend = instance

  private def createStateBackend(): StateBackend = {
    val backendType: String = Config.getString("state.backend")
    backendType match {
      case "RocksDBStateBackendImpl" => new RocksDBStateBackendImpl()
      case "MapStateBackendImpl" => new MapStateBackendImpl()
      case _ => throw new IllegalArgumentException("Invalid StateBackend configuration")
    }
  }
}
