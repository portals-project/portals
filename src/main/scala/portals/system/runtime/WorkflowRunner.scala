package portals

import java.util.concurrent.Flow.Subscription
import java.util.concurrent.locks.ReentrantLock

private[portals] object WorkflowRunner:
  def run(workflow: Workflow)(using system: SystemContext): Unit = 
    case class WrappedEvent(id: String, event: Any)

    // get sources and the others
    val all = workflow.tasks.map(_._1).toSet
    val startpoints = workflow.connections.map(_._1).toSet
    val endpoints = workflow.connections.map(_._2).toSet
    val sources = startpoints.diff(endpoints).toList
    val others = all.diff(sources.toSet).toList

    // create source behaviors that wrap things
    val sourcesBehaviors = sources.map { name =>
      TaskBehaviors.map{ event => WrappedEvent(name, event) }
    }

    // create proxy source behaviors that filter and unwrap things
    val proxyBehaviors = sources.map { name =>
      TaskBehaviors.flatMap[WrappedEvent, Any] { event => 
        if (event.id == name) List(event.event) else List.empty 
      }
    }
    
    // create a phantom sequencer behavior
    val sequencerBehavior = new OperatorSpec[Any, Any] {
      val lock = new ReentrantLock()

      // events is a map from subscription to events of the events of the latest
      // atom that is being received.
      @volatile var events: Map[Int, List[Any]] = Map.empty

      def onNext(subscriptionId: Int, item: Any): WithContext[Any, Any, Unit] = 
        lock.lock()
        // add event to the building set of particles for the new atom
        val particles = item :: events(subscriptionId)
        events = events + (subscriptionId -> particles)
        lock.unlock()

      def onComplete(subscriptionId: Int): WithContext[Any, Any, Unit] = 
        lock.lock()
        seal()
        lock.unlock()
        
      def onError(subscriptionId: Int, error: Throwable): WithContext[Any, Any, Unit] = 
        lock.lock()
        lock.unlock()
        ???

      def onSubscribe(subscriptionId: Int, subscription: Subscription): WithContext[Any, Any, Unit] = 
        lock.lock()
        events = events + (subscriptionId -> Nil)
        subscription.request(Long.MaxValue)
        lock.unlock()

      def onAtomComplete(subscriptionId: Int): WithContext[Any, Any, Unit] = 
        lock.lock()
        // finish atom from particles
        val atom = events(subscriptionId).toList.reverse
        // send events 
        atom.foreach { event =>
          submit(event)
        }
        // fuse atom
        fuse()
        // remove atom from events
        events = events + (subscriptionId -> Nil)
        lock.unlock()
    }

    // launch the behaviors
    val othersAndRefs = others.map { name =>
      val tasksMap = workflow.tasks.toMap
      val behavior = tasksMap(name)
      val (iref, oref): (IStreamRef[_], OStreamRef[_]) = TaskRunner.run(behavior)  
      (name, (iref, oref))
    }
    
    val sourcesAndRefs = sources.zip(sourcesBehaviors).map { (name, behavior) =>
      val (iref, oref): (IStreamRef[_], OStreamRef[_]) = TaskRunner.run(behavior)  
      (name, (iref, oref))
    }

    val proxyAndRefs = sources.zip(proxyBehaviors).map { (name, behavior) =>
      val (iref, oref): (IStreamRef[_], OStreamRef[_]) = TaskRunner.run(behavior)  
      (name + "#proxy", (iref, oref))
    }

    val phantomAndRefs = List(
      ("phantomSequencer", 
      {
        val opref = system.executionContext.execute(sequencerBehavior)
        val iref = new IStreamRef[Any]{
          val opr = opref
          def submit(event: Any): Unit = ???
          def seal(): Unit = ???
          def fuse(): Unit = ???
        }
        val oref = new OStreamRef[Any]{
          val opr = opref
          def subscribe(subscriber: IStreamRef[Any]): Unit = opr.subscibe(subscriber.opr)
        }
        (iref, oref)
      })
    )

    val allAndRefs = (othersAndRefs ++ sourcesAndRefs ++ proxyAndRefs ++ phantomAndRefs)

    // add to registry
    allAndRefs.foreach { (name, refs) =>
        val hname = workflow.name + "/" + name
        system.registry.set(hname, refs.asInstanceOf)
      }

    ////////////////////////////////////////////////////////////////////////////
    // connect all
    ////////////////////////////////////////////////////////////////////////////

    // connect sources to phantom sequencer
    sourcesAndRefs.foreach { (name, refs) =>
      val (iref, oref) = refs
      val sub = phantomAndRefs(0)._2._1
      oref.subscribe(sub.asInstanceOf)
    }

    // connect phantom sequencer to proxysources
    proxyAndRefs.foreach { (name, refs) =>
      val (iref, oref) = refs
      val pub = phantomAndRefs(0)._2._2
      pub.subscribe(iref.asInstanceOf)
    }

    // connect proxysources to tasks
    proxyAndRefs.foreach { (name, refs) =>
      // hack, using # here instead as $ used for another purpose atm
      val origName = name.split("#")(0) 
      val (iref, oref) = refs
      workflow.connections.foreach { (from, to) =>
        if origName == from then
          val toRef = allAndRefs.toMap.get(to).get._1
          oref.subscribe(toRef.asInstanceOf)
      }
    }

    // connect others
    workflow.connections.foreach { (from, to) =>
      if !sources.contains(from) then
        val fromRef = allAndRefs.toMap.get(from).get._2
        val toRef = allAndRefs.toMap.get(to).get._1
        fromRef.subscribe(toRef.asInstanceOf)
    }

end WorkflowRunner