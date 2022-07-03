package pods.workflows

import java.util.concurrent.Flow.Subscription
import java.util.concurrent.locks.ReentrantLock

object OperatorSpecs:
  /** We create a new OperatorSpec from the TaskBehavior and TaskContext. */
  def opSpecFromTaskBehavior[T, U](ctx: TaskContext[T, U], task: TaskBehavior[T, U]): OperatorSpec[T, U] =
    new OperatorSpec[T, U]{
      // FIXME: IT IS NOT SAFE TO INSTANTIATE MULTIPLE EXECUTIONS FROM THE SAME 
      // OPERATORSPEC, THIS SHOULD HOWEVER BE SAFE. THE ISSUE IS THAT THE BLOCKING
      // AND NONBLOCKING SETS ARE OTHERWISE SHARED BETWEEN THE EXECUTIONS, WHICH
      // OF COURSE IS NOT SAFE.

      val lock = ReentrantLock()
      var blocking = Set.empty[Int] // contains subscriptionId 
      var nonBlocking = Set.empty[Int] // contains subscriptionId 

      /** Atom Stash, for stashing atoms and later process them by unstashing */
      // for each subscriptionId, a list of atoms
      @volatile var _atom_stash = Map.empty[Int, List[List[T]]]
      // stash an atom for the subscriptionId
      private def atom_stash(subscriptionId: Int, atom: List[T]) =
        _atom_stash = _atom_stash.updated(subscriptionId, _atom_stash(subscriptionId) :+ atom)
      // unstash one atom for each subscriptionId
      private def atom_unstash_one(): WithContext[T, U, Unit] = 
        _atom_stash.foreach{ case (subscriptionId, atoms) =>
          val atom = atoms.head
          atom.foreach(x => onNext(-1, x))
        }
        _atom_stash = _atom_stash.mapValues(_.tail).toMap
      // get blocking subscriptionIds
      private def atom_stash_get_blocking(): Set[Int] =
        _atom_stash.filter{ case (subscriptionId, atoms) =>
          !atoms.isEmpty
        }.keySet

      /** Stash, for stashing a building atom (unfinished atom) */
      @volatile var _stash = Map.empty[Int, List[T]]
      // add item to building stash
      private def stash(subscriptionId: Int, item: T): WithContext[T, U, Unit] = 
        _stash = _stash.updated(subscriptionId, _stash(subscriptionId) :+ item)
      // fuse the stashed atom for the subscriptionId
      private def stash_fuse(subscriptionId: Int): WithContext[T, U, Unit] =
        atom_stash(subscriptionId, _stash(subscriptionId))
        _stash = _stash.updated(subscriptionId, Nil)

      private def _stash_init(subscriptionId: Int): Unit =
        _atom_stash = _atom_stash.updated(subscriptionId, List.empty)
        _stash = _stash.updated(subscriptionId, List.empty)

      def onNext(subscriptionId: Int, item: T): WithContext[T, U, Unit] =
        lock.lock() 
        if subscriptionId == -1 then
          task.onNext(ctx)(item)
        else
          if (blocking.contains(subscriptionId)) then
            stash(subscriptionId, item)
          else
            task.onNext(ctx)(item)
        lock.unlock() 
          
      def onComplete(subscriptionId: Int): WithContext[T, U, Unit] =
        lock.lock() 
        task.onComplete(ctx)
        lock.unlock() 

      def onError(subscriptionId: Int, error: Throwable): WithContext[T, U, Unit] =
        lock.lock()
        task.onError(ctx)(error) 
        lock.unlock()  
      
      def onSubscribe(subscriptionId: Int, subscription: Subscription): WithContext[T, U, Unit] =
        lock.lock()
        nonBlocking = nonBlocking + subscriptionId
        // FIXME: we should implement some form of backpressure.
        _stash_init(subscriptionId)
        subscription.request(Long.MaxValue)
        lock.unlock() 

      def onAtomComplete(subscriptionId: Int): WithContext[T, U, Unit] =
        lock.lock() 
        if subscriptionId == -1  then
          task.onAtomComplete(ctx)
        else
          stash_fuse(subscriptionId)
          blocking = blocking + subscriptionId
          nonBlocking = nonBlocking - subscriptionId
          if (nonBlocking.isEmpty) then
            atom_unstash_one()
            task.onAtomComplete(ctx)
            nonBlocking = blocking
            blocking = Set.empty
            // set blocking based on atom_stash
            blocking = atom_stash_get_blocking()
            nonBlocking = nonBlocking.diff(blocking)
        lock.unlock() 
    }