package pods.workflows

import java.util.concurrent.Flow.Subscription

object OperatorSpecs:
  /** We create a new OperatorSpec from the TaskBehavior and TaskContext. 
      * 
      * This also implements the following Atom-Alignment Protocol:
      *   def onSubscribe(id, subscription) = 
      *     add id to nonBlocking
      *   def onNext(id, item) = 
      *     if id is blocking then stash item else submit item
      *   def onAtomComplete(id) = 
      *     set id as blocking
      *     if all ids are blocking, then
      *       fuse atom
      *       and process the whole stash
      *       set all ids to non-blocking
     */
  def opSpecFromTaskBehavior[T, U](ctx: TaskContext[T, U], task: TaskBehavior[T, U]): OperatorSpec[T, U] =
    new OperatorSpec[T, U]{
      // FIXME: IT IS NOT SAFE TO INSTANTIATE MULTIPLE EXECUTIONS FROM THE SAME 
      // OPERATORSPEC, THIS SHOULD HOWEVER BE SAFE. THE ISSUE IS THAT THE BLOCKING
      // AND NONBLOCKING SETS ARE OTHERWISE SHARED BETWEEN THE EXECUTIONS, WHICH
      // OF COURSE IS NOT SAFE.

      var blocking = Set.empty[Int] // contains subscriptionId 
      var nonBlocking = Set.empty[Int] // contains subscriptionId 

      /** Stash, for stashing items and later process them by unstashing */
      var _stash = List.empty[T]
      private def stash(item: T): WithContext[T, U, Unit] = 
        _stash = item :: _stash

      private def unstash(): WithContext[T, U, Unit] =
        _stash.foreach(x => onNext(-1, x))
        _stash = List.empty

      def onNext(subscriptionId: Int, item: T): WithContext[T, U, Unit] = 
        if (blocking.contains(subscriptionId)) then
          stash(item)
        else
          task.onNext(ctx)(item)
          
      def onComplete(subscriptionId: Int): WithContext[T, U, Unit] = 
        task.onComplete(ctx)

      def onError(subscriptionId: Int, error: Throwable): WithContext[T, U, Unit] =
        task.onError(ctx)(error)  
      
      def onSubscribe(subscriptionId: Int, subscription: Subscription): WithContext[T, U, Unit] =
        nonBlocking = nonBlocking + subscriptionId
        // FIXME: we should implement some form of backpressure.
        subscription.request(Long.MaxValue)

      def onAtomComplete(subscriptionId: Int): WithContext[T, U, Unit] = 
        blocking = blocking + subscriptionId
        nonBlocking = nonBlocking - subscriptionId
        if (nonBlocking.isEmpty) then
          task.onAtomComplete(ctx)
          unstash()
          nonBlocking = blocking
          blocking = Set.empty
    }