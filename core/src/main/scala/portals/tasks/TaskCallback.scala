package portals

// TODO: add information on how this is used
trait TaskCallback[T, U]:
  def submit(key: Key[Int], event: U): Unit

  // deprecated
  // def fuse(): Unit
end TaskCallback // trait
