package portals

trait TaskCallback[T, U]:
  def submit(item: U): Unit
  def fuse(): Unit
end TaskCallback // trait