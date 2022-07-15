package portals

object Systems:
  def syncLocal(): LocalSystemContext = new SyncLocalSystem()

