package portals

// TODO: add information on how this is used
trait TaskCallback[T, U]:
  def submit(item: U): Unit
  def fuse(): Unit
end TaskCallback // trait
