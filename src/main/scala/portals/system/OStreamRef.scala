package portals

/** OStreamRef */
trait OStreamRef[T]:
  private[portals] def subscribe(subscriber: IStreamRef[T]): Unit

  private[portals] def setPreSubmitCallback(cb: PreSubmitCallback[T]): Unit = ???

trait PreSubmitCallback[T]:
  def preSubmit(event: T): Unit = ()
  def preFuse(): Unit = ()
