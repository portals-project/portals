package portals

object Systems:
  def local(): SystemContext = new LocalSystem()
  def syncLocal(): LocalSystemContext = new SyncLocalSystem()

