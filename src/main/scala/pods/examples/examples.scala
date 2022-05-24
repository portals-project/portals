package pods.examples

import pods.workflows.*

/** Examples of how to use Pods Workflows. */

/** Basic Workflow with single input single output and one task.
  */
@main def exampleBasic() =
  val wf = Workflows // [[Workflow]]s factory
    // returns a WorkflowBuilder
    .builder()
    // add a source to the workflow with type Int with name "src"
    .source[Int]("src")
    // add a map transformation stage to the workflow with output type String
    .map[Int, String](x => "Number: " + x.toString)
    // log output
    .withLogger()
    // add a sink to the workflow with type String with name "sink"
    .sink[String]("sink")
    // assemble the workflow
    .build()

  (0 until 8).foreach(x =>
    // submit events to the workflow into source "src"
    wf.submit("src", x)
  )

  wf.close() // blocking call to close the workflow

/** Workflow with Virtual State Machines
  */
@main def exampleVSM() =
  import DSL.ctx // import implicit ctx

  // VSM is a virtual state machine
  object VSM:
    sealed trait I
    case class A(x: Int) extends I
    case class B(x: Int) extends I
    case class C(x: Int) extends I
    sealed trait O
    case class D(x: Int) extends O

    def apply(): TaskBehavior[I, O] = initState()

    def initState(): TaskBehavior[I, O] = TaskBehaviors.vsm {
      case A(x) => // emit D(x) and stay in state
        val d = D(x)
        ctx.emit(d)
        TaskBehaviors.same // use the same behavior as last
      case B(x) => // change into state otherState
        otherState(x)
      case C(_) => // stay in state
        TaskBehaviors.same
    }

    def otherState(state: Int): TaskBehavior[I, O] = TaskBehaviors.vsm {
      case A(x) => // emit D(x) and stay in state
        val d = D(state)
        ctx.emit(d)
        TaskBehaviors.same
      case B(x) => // stay in state
        TaskBehaviors.same
      case C(x) => // change into initState
        initState()
    }

  val wf = Workflows
    .builder()
    .source[VSM.I]("src")
    .vsm(VSM())
    .withLogger()
    .sink[VSM.O]("sink")
    .build()

  // should log 1, 2, 3, 3, 7, 8
  wf.submit("src", VSM.A(1))
  wf.submit("src", VSM.A(2))
  wf.submit("src", VSM.B(3))
  wf.submit("src", VSM.A(4))
  wf.submit("src", VSM.A(5))
  wf.submit("src", VSM.C(6))
  wf.submit("src", VSM.A(7))
  wf.submit("src", VSM.A(8))

  wf.close()

/** The VSM can alternatively be created directly from within the
  * WorkflowBuilder
  */
@main def exampleVSMAlternative() =
  // it is also possible to define the VSM directly within the builder using the TaskBehaviors factories
  val wf = Workflows
    .builder()
    .source[Int]("src")
    .vsm(
      TaskBehaviors.vsm(ctx ?=>
        event =>
          ctx.emit(event)
          TaskBehaviors.same
      )
    )
    .withLogger()
    .sink[Int]("sink")
    .build()

  wf.submit("src", 0)
  wf.close()

/** Microservices Example
  *
  * To support microservices we have two patterns.
  *   i. The microservice is implemented as an open workflow (inputs and outputs
  *      not connected). Then, we connect our service (which also is a workflow)
  *      to the microservice workflow.
  *   i. The microservice is implemented as an actor (an actor is a closed
  *      workflow). Then we connect our service to the workflow by sending (ask)
  *      the microservice it a request.
  */
@main def exampleMicroservice() =

  case class Request(x: Int)
  case class Reply(x: Int)

  // let us define a microservice that decrements numbers by 1
  val microservice = Workflows
    .builder()
    .source[Request]("src")
    .flatMap({
      case Request(x) if x > 0 =>
        List(Reply(x - 1))
      case Request(x) =>
        List.empty
    })
    .sink[Reply]("sink")
    .build()

  // let us define a service that logs the requests
  val service = Workflows
    .builder()
    .source[Reply]("src")
    .withLogger()
    .map({ case Reply(x) =>
      Request(x)
    })
    .sink[Request]("sink")
    .build()

  // and now, let us connect the two workflows, we choose to form a cycle
  Workflows.connect(service.sink("sink"), microservice.source("src"))
  Workflows.connect(microservice.sink("sink"), service.source("src"))

  // we send 8 to the service, and we expect it to count down from 8 to 0 due to the decrementing cycle.
  service.submit("src", Reply(8))

  microservice.close()
  service.close()

/** Microservices example using actors */
@main def exampleMicroservice2() =
  case class Request(x: Int, replyTo: IStream[Reply])
  case class Reply(x: Int)

  // let us define a microservice that decrements numbers by 1
  val microservice = Workflows
    .builder()
    .source[Request]("src")
    .processor({ ctx ?=> event =>
      event match
        case Request(x, replyTo) =>
          ctx.send(replyTo, Reply(x - 1))
    })
    .withLogger()
    .build()

  // let us define a service that logs the requests
  val service = Workflows
    .builder()
    .source[Int]("src")
    .processor({ ctx ?=> event =>
      event match
        case Reply(x) =>
          if (x > 0) then
            val request = Request(x, ctx.self.asInstanceOf)
            val iref = microservice.source("src").iref
            ctx.send(iref, request.asInstanceOf)
            ctx.emit(x)
          else ctx.emit(x)

        case x: Int =>
          val request = Request(x, ctx.self.asInstanceOf)
          val iref = microservice.source("src").iref
          ctx.send(iref, request.asInstanceOf)
    })
    .withLogger()
    .sink[Request]("sink")
    .build()

  service.submit("src", 8)
  microservice.close()
  service.close()

/** Actors
  *
  * Workflows are as powerful as actors. This example shows how workflows can be
  * actors.
  */
@main def exampleActors() =
  // PingPong example

  // Actor factor for PingPong actor
  object ActorExample:
    import pods.workflows.DSL.*

    sealed trait Events
    case class Ping(x: Int, replyTo: IStream[Pong]) extends Events
    case class Pong(x: Int, replyTo: IStream[Ping]) extends Events

    def behavior = TaskBehaviors.vsm[Events, Events]({
      case e @ Ping(x, replyTo) if x > 0 =>
        ctx.log.info(e.toString)
        replyTo ! Pong(x - 1, ctx.self.asInstanceOf)
        TaskBehaviors.same
      case e @ Pong(x, replyTo) if x > 0 =>
        ctx.log.info(e.toString)
        replyTo ! Ping(x - 1, ctx.self.asInstanceOf)
        TaskBehaviors.same
      case _ =>
        TaskBehaviors.same
    })

  import ActorExample.*

  val pinger = Workflows
    .builder()
    .source[Events]("src")
    .vsm(behavior)
    .withLogger()
    .build()

  val ponger = Workflows
    .builder()
    .source[Events]("src")
    .vsm(behavior)
    .withLogger()
    .build()

  // this will log two concurrent runs from 8 to 0
  pinger.submit("src", Ping(8, ponger.source("src").iref.asInstanceOf))
  pinger.submit("src", Ping(8, ponger.source("src").iref.asInstanceOf))

  pinger.close()
  ponger.close()

/** Ask and Await */
@main def exampleAskAwait() =
  import DSL.*

  case class Request(x: Int, replyTo: IStream[Reply])
  case class Reply(x: Int)
  case class Start(x: Int, target: IStream[Request])

  val replyer = Actors.receive[Request]({ case Request(x, replyTo) =>
    replyTo ! Reply(x - 1)
    Actors.same
  })

  val asker = Actors.receive[Reply | Start]({
    case Start(x, target) =>
      val future = ctx.ask(target, ref => Request(x, ref))
      ctx.await(future)(r =>
        ctx.log.info(r.toString)
        Actors.same
      )
    case _ =>
      ??? // shouldn't happen
  })

  val wfReplyer = Workflows
    .builder()
    .actor(replyer, "replyer")
    .build()

  val wfAsker = Workflows
    .builder()
    .actor(asker, "asker")
    .build()

  wfAsker.submit(
    "asker",
    Start(8, wfReplyer.source("replyer").iref.asInstanceOf)
  )

  wfAsker.close()
  wfReplyer.close()



@main def workerWithSenderExample(): Unit =
  import Workers.*
  type T = String

  case class Event[T](sender: String, event: T)

  val worker1 = Workers[T, Event[T]]()
    .withOnNext(worker ?=>
      event =>
        val e = Event("worker1", event)
        worker.submit(e)
    )
    .build()

  val worker2 = Workers[T, Event[T]]()
    .withOnNext(worker ?=>
      event =>
        val e = Event("worker2", event)
        worker.submit(e)
    )
    .build()

  val worker3 = Workers[Event[T], T]()
    .withOnNext(event => println(event))
    .build()

  // worker3 subscribes to both worker1 and worker2
  worker1.subscribe(worker3)
  worker2.subscribe(worker3)

  worker1.onNext("hello world")
  worker1.onNext("hello world")
  worker1.onNext("hello world")
  worker1.onNext("hello world")
  worker1.onNext("hello world")
  worker2.onNext("hello world")
  Thread.sleep(100)

@main def testTask() =
  val task1 = Tasks(TaskBehaviors.processor[Int, Int]({ tctx ?=> x =>
    tctx.log.info(s"task1: $x")
    tctx.emit(x + 1)
    TaskBehaviors.same
  }))

  val task2 = Tasks(TaskBehaviors.processor[Int, Int]({ tctx ?=> x =>
    tctx.log.info(s"task2: $x")
    tctx.emit(x + 1)
    TaskBehaviors.same
  }))

  Tasks.connect(task1, task2)

  println("should log twice, once for each task")

  task1.tctx.mainiref.worker.submit(1)

  Thread.sleep(100)
