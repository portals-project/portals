package portals

/** Internal API. OutputCollector to collect submitted events as side effects.
  * Works for all kinds of tasks.
  */
private[portals] class OutputCollectorImpl[T, U, Req, Rep] extends OutputCollector[T, U, Req, Rep]:
  //////////////////////////////////////////////////////////////////////////////
  // Task
  //////////////////////////////////////////////////////////////////////////////
  private var _output = List.empty[WrappedEvent[U]]
  def submit(event: WrappedEvent[U]): Unit = _output = event :: _output
  def getOutput(): List[WrappedEvent[U]] = _output.reverse
  def clear(): Unit = _output = List.empty

  //////////////////////////////////////////////////////////////////////////////
  // Asker Task
  //////////////////////////////////////////////////////////////////////////////
  private var _asks = List.empty[Ask[Req]]
  override def ask(
      portal: String,
      askingTask: String,
      req: Req,
      key: Key[Long], // TODO: this is confusing exactly which key is meant here, askingKey or replyingKey
      id: Int
  ): Unit =
    _asks = Ask(key, PortalMeta(portal, askingTask, key, id), req) :: _asks
  def getAskOutput(): List[Ask[Req]] = _asks.reverse
  def clearAsks(): Unit = _asks = List.empty

  //////////////////////////////////////////////////////////////////////////////
  // Replier Task
  //////////////////////////////////////////////////////////////////////////////
  private var _reps = List.empty[Reply[Rep]]
  override def reply(
      r: Rep,
      portal: String,
      askingTask: String,
      key: Key[Long], // TODO: this is confusing exactly which key is meant here, askingKey or replyingKey
      id: Int
  ): Unit = _reps = Reply(key, PortalMeta(portal, askingTask, key, id), r) :: _reps
  def getRepOutput(): List[Reply[Rep]] = _reps.reverse
  def clearReps(): Unit = _reps = List.empty
end OutputCollectorImpl // class
