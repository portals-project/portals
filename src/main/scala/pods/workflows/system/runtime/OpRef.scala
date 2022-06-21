package pods.workflows

trait OpRef[T, U]:
  val mop: MultiOperatorWithAtom[T, U]
  def submit(item: U): Unit
  def seal(): Unit
  def subscibe(mref: OpRef[U, _]): Unit
  def fuse(): Unit
end OpRef // trait

object OpRef:
  /** Factory to create a OpRef from a MultiOperator */
  def fromMultiOperator[T, U](_mop: MultiOperatorWithAtom[T, U]): OpRef[T, U] = 
    new OpRef[T, U]{
      val mop = _mop
      def submit(item: U): Unit = mop.submit(Event(item))
      def seal(): Unit = mop.seal()
      def subscibe(mref: OpRef[U, _]): Unit = mop.subscribe(mref.mop)
      def fuse(): Unit = mop.submit(Atom())
    }
end OpRef // object