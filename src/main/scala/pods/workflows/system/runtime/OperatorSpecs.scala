package pods.workflows

import java.util.concurrent.Flow.Subscription

// private[pods] trait TaskContext[I, O]:
//   type WithContext[S] = OperatorCtx[I, O] ?=> S
//   def emit(item: O): WithContext[Unit] = 
//     summon[OperatorCtx[I, O]].submit(item)
//   def fuse(): WithContext[Unit] = 
//     summon[OperatorCtx[I, O]].fuse()

// private[pods] trait TaskBehavior[I, O]:
//   type WithContext[S] = OperatorCtx[I, O] ?=> S

//   def onNext(ctx: TaskContext[I, O])(t: I): WithContext[TaskBehavior[I, O]]
//   def onError(ctx: TaskContext[I, O])(t: Throwable): WithContext[TaskBehavior[I, O]]
//   def onComplete(ctx: TaskContext[I, O]): WithContext[TaskBehavior[I, O]]
//   def onAtomComplete(ctx: TaskContext[I, O]): WithContext[TaskBehavior[I, O]]

object OperatorSpecs:
  def opSpecFromTaskBehavior[T, U](ctx: TaskContext[T, U], task: TaskBehavior[T, U]): OperatorSpec[T, U] =

    // TODO: alignment now works for tasks, but still work needs to be done to 
    // synchronize amongst the sources (and sinks).
    // TODO: IT IS NOT SAFE TO INSTANTIATE MULTIPLE EXECUTIONS FROM THE SAME 
    // OPERATORSPEC, THIS SHOULD HOWEVER BE SAFE. THE ISSUE IS THAT THE BLOCKING
    // AND NONBLOCKING SETS ARE OTHERWISE SHARED BETWEEN THE EXECUTIONS, WHICH
    // OF COURSE IS NOT SAFE.
    new OperatorSpec[T, U]{
      /** Translation also implements Atom-Alignment Protocol.
        * 
        * Alignment protocol:
        * def onSubscribe(id, subscription) = 
        *   add id to nonBlocking
        * def onNext(id, item) = 
        *   if id is blocking then stash item else submit item
        * def onAtomComplete(id) = 
        *   set id as blocking
        *   if all ids are blocking, then
        *     fuse atom
        *     and process the whole stash
        *     set all ids to non-blocking
       */

      var blocking = Set.empty[Int] // contains subscriptionId 
      var nonBlocking = Set.empty[Int] // contains subscriptionId 

      /** Stash, for stashing items and later process them by unstashing */
      var _stash = List.empty[T]
      private def stash(item: T): WithContext[Unit] = 
        _stash = item :: _stash

      private def unstash(): WithContext[Unit] =
        _stash.foreach(x => onNext(-1, x))
        _stash = List.empty

      def onNext(subscriptionId: Int, item: T): WithContext[Unit] = 
        if (blocking contains subscriptionId) then
          stash(item)
        else
          task.onNext(ctx)(item)
          
      def onComplete(subscriptionId: Int): WithContext[Unit] = 
        task.onComplete(ctx)

      def onError(subscriptionId: Int, error: Throwable): WithContext[Unit] =
        task.onError(ctx)(error)  
      
      def onSubscribe(subscriptionId: Int, subscription: Subscription): WithContext[Unit] =
        nonBlocking = nonBlocking + subscriptionId
        subscription.request(Long.MaxValue)

      def onAtomComplete(subscriptionId: Int): WithContext[Unit] = 
        blocking = blocking + subscriptionId
        nonBlocking = nonBlocking - subscriptionId
        if (nonBlocking.isEmpty) then
          task.onAtomComplete(ctx)
          unstash()
          nonBlocking = blocking
          blocking = Set.empty
    }