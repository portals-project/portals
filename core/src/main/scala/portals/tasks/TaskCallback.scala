package portals

trait TaskCallback[T, U]:
  def submit(key: Key[Int], event: U): Unit
end TaskCallback // trait

trait PortalTaskCallback[T, U, Req, Rep] extends TaskCallback[T, U]:
  def ask(portal: AtomicPortalRefKind[Req, Rep])(req: Req)(key: Key[Int], id: Int): Unit
  def reply(r: Rep)(key: Key[Int], id: Int): Unit
end PortalTaskCallback // trait
