package pods.workflows

class GlobalRegistry:
  private var map: Map[String, (IStreamRef[_], OStreamRef[_])] = Map()

  def apply[T](name: String): RemoteIStreamRef[T] = irefs(name)

  def irefs[T](name: String): RemoteIStreamRef[T] = 
    RemoteIStreamRef[T](
      map.get(name).get._1.asInstanceOf[IStreamRef[T]]
    )

  def orefs[T](name: String): RemoteOStreamRef[T] = 
    RemoteOStreamRef[T](
      map.get(name).get._2.asInstanceOf[OStreamRef[T]]
    )

  private[pods] def set[I, O](name: String, refs: (IStreamRef[I], OStreamRef[O])): Unit = 
    map += (name -> refs)
