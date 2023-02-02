package portals.benchmark.systems

import scala.collection.immutable.VectorBuilder
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.util.Timeout

import com.typesafe.config.ConfigFactory

import portals.*

class DataParallelSystem(val numPartitions: Int, val parallelism: Int = 32) extends PortalsSystem:
  import AkkaRunner.Events.*

  val config = ConfigFactory
    .parseString(
      s"""
      akka {
        log-dead-letters-during-shutdown = off
        log-dead-letters = off
        actor {
          default-dispatcher {
            fork-join-executor {
              parallelism-min = ${parallelism}
              parallelism-max = ${parallelism}
              parallelism-factor = 1.0
            }
          }
        }
      }
      """
    )
    .withFallback(ConfigFactory.defaultApplication)

  given timeout: Timeout = Timeout(3.seconds)
  given system: akka.actor.ActorSystem = akka.actor.ActorSystem("Portals", config)
  given scheduler: akka.actor.typed.Scheduler = system.toTyped.scheduler

  class PartitionedMetadata(val partition: Int):
    var streams: Map[String, ActorRef[PubSubRequest]] = Map.empty
    var sequencers: Map[String, ActorRef[Event[_]]] = Map.empty
    var generators: Map[String, ActorRef[GeneratorCommand]] = Map.empty
    var workflows: Map[String, Map[String, ActorRef[Event[_]]]] = Map.empty

  // for each nuPartitions
  val partitions = (0 until numPartitions).map(i => new PartitionedMetadata(i)).toList

  val registry: GlobalRegistry = null

  val akkaRunner = AkkaRunnerImpl

  private def launchStream[T](partition: Int, stream: AtomicStream[T]): Unit =
    val aref = system.spawnAnonymous(akkaRunner.atomicStream(stream.path))
    partitions(partition).streams += stream.path -> aref

  def launchSequencer[T](partition: Int, sequencer: AtomicSequencer[T]): Unit =
    val stream = partitions(partition).streams(sequencer.stream.path)
    val aref = system.spawnAnonymous(akkaRunner.sequencer[T](sequencer.path, sequencer.sequencer, stream))
    partitions(partition).sequencers += sequencer.path -> aref.asInstanceOf[ActorRef[Event[_]]]

  def launchConnection[T](partition: Int, connection: AtomicConnection[T]): Unit =
    akkaRunner.connect(
      partitions(partition).streams(connection.from.path),
      partitions(partition).sequencers(connection.to.path)
    )

  def launchGenerator[T](partition: Int, generator: AtomicGenerator[T]): Unit =
    val stream = partitions(partition).streams(generator.stream.path)
    val aref =
      system.spawnAnonymous(akkaRunner.generator[T](generator.path, generator.generator, stream))
    partitions(partition).generators += generator.path -> aref

  // TODO: add shuffle steps at key-by.
  def launchWorkflow[T, U](partition: Int, workflow: Workflow[T, U]): Unit =
    val stream = partitions(partition).streams(workflow.stream.path)

    var runtimeWorkflow: Map[String, ActorRef[Event[_]]] = Map.empty

    {
      val deps = workflow.connections.filter(_._2 == workflow.sink).map(x => x._1).toSet
      runtimeWorkflow =
        runtimeWorkflow + (workflow.sink -> system.spawnAnonymous(akkaRunner.sink(workflow.sink, Set(stream), deps)))
    }

    // here we assume the connections are topologically sorted :)
    workflow.connections.foreach { (from, to) =>
      if !runtimeWorkflow.contains(to) && workflow.tasks.contains(to) then
        val toto = workflow.connections.filter(_._1 == to).map(x => x._2).toSet
        val deps = workflow.connections.filter(_._2 == to).map(x => x._1).toSet
        val aref = system.spawnAnonymous(
          akkaRunner.task[Any, Any](
            to,
            workflow.tasks(to).asInstanceOf[GenericTask[Any, Any, Nothing, Nothing]],
            runtimeWorkflow.filter(x => toto.contains(x._1)).map(_._2).toSet,
            deps
          ),
        )
        runtimeWorkflow = runtimeWorkflow + (to -> aref)
    }

    {
      val toto = workflow.connections.filter(_._1 == workflow.source).map(x => x._2)
      val aref = system.spawnAnonymous(
        akkaRunner.source(
          workflow.source,
          runtimeWorkflow.filter(x => toto.contains(x._1)).map(_._2).toSet
        )
      )
      akkaRunner.connect(partitions(partition).streams(workflow.consumes.path), aref)
      runtimeWorkflow = runtimeWorkflow + (workflow.source -> aref)
    }

    partitions(partition).workflows += (workflow.path -> runtimeWorkflow)

  def launch(application: Application): Unit =
    partitions.foreach { partitionmeta =>
      val partition = partitionmeta.partition

      // first launch all streams
      application.streams.foreach { stream => launchStream(partition, stream) }

      // then launch all sequencers
      application.sequencers.foreach { sequencer => launchSequencer(partition, sequencer) }

      // launch all connections
      application.connections.foreach { connection => launchConnection(partition, connection) }

      // then launch all workflows
      application.workflows.foreach { workflow => launchWorkflow(partition, workflow) }
    }

    // then launch all generators
    application.generators.foreach { generator =>
      partitions.foreach { partitionmeta =>
        val partition = partitionmeta.partition
        val nPartitions = partitions.size

        val g =
          generator.generator.asInstanceOf[DataParallel.DataParallelGenerator[_]].toPartition(partition, nPartitions)
        val _gen = AtomicGenerator(generator.path, generator.stream, g.asInstanceOf)

        launchGenerator(partition, _gen)
      }
    }

  def shutdown(): Unit =
    Await.result(system.terminate(), 5.seconds)

object DataParallel:
  import portals.Generator.*
  trait DataParallelGenerator[T] extends Generator[T]:
    override def generate(): GeneratorEvent[T] = ??? // not used
    override def hasNext(): Boolean = ??? // not used

    def toPartition(partition: Int, nPartitions: Int): Generator[T]
  end DataParallelGenerator

  class DataParallelGeneratorIntImpl(from: Int, until: Int, step: Int) extends DataParallelGenerator[Int]:
    override def toPartition(partition: Int, nPartitions: Int): Generator[Int] =
      val range = Iterator.range(from + partition, until, nPartitions).grouped(step).map(_.iterator)
      Generators.fromIteratorOfIterators(range)
  end DataParallelGeneratorIntImpl

  class DataParallelGeneratorFromListImpl[T](list: List[T]) extends DataParallelGenerator[T]:
    override def toPartition(partition: Int, nPartitions: Int): Generator[T] =
      Generators.fromIterator(list.iterator)

  def fromRange(from: Int, until: Int, step: Int): DataParallelGenerator[Int] =
    DataParallelGeneratorIntImpl(from, until, step)

  def fromList[T](list: List[T]): DataParallelGenerator[T] =
    DataParallelGeneratorFromListImpl(list)
/*
// object ShuffleSender:
//   // given behaviorConversion[T]: Conversion[Any, Behavior[T]] with
//   //   def apply(x: Any): Behavior[T] = Behaviors.same

//   // initiate the shuffle with the shuffle receivers in a partition map
//   def apply(
//       partition: Int,
//       path: String,
//       subscribers: PartitionMapImpl[ActorRef[PubSubRequest]]
//   ): Behavior[PubSubRequest] =
//     Behaviors.setup { ctx =>
//       Behaviors.receiveMessage {
//         case portals.system.async.Events.Subscribe(subscriber, replyTo) => ??? // not supported

//         case portals.system.async.Events.Event(sender, event) =>
//           event match
//             case Event(key, value) =>
//               subscribers.get(key.x) match
//                 case Some(partition) =>
//                   partition ! portals.system.async.Events.Event(path, event)
//                   Behaviors.same
//                 case None => ???
//             case _ =>
//               subscribers.values().foreach { _ ! portals.system.async.Events.Event(path, event) }
//               Behaviors.same
//       }
//     }

// object ShuffleReceiver:
//   // given behaviorConversion[T]: Conversion[Any, Behavior[T]] with
//   //   def apply(x: Any): Behavior[T] = Behaviors.same

//   // initiate the shuffle receiver with its subscribers and with the shuffle senders
//   def apply[T](
//       partition: Int,
//       path: String,
//       senders: Set[String],
//       subscribers: Set[ActorRef[PubSubRequest]]
//   ): Behavior[PubSubRequest] =
//     Behaviors.setup { ctx =>

//       // number of buffered atoms per sender
//       var blocking: Map[String, Int] = senders.map { x => (x -> 0) }.toMap
//       var seald: Set[String] = Set.empty

//       var atoms: Map[String, Vector[Vector[WrappedEvent[T]]]] = senders.map { x => (x -> Vector.empty) }.toMap
//       var buffer: Map[String, VectorBuilder[WrappedEvent[T]]] = senders.map { x =>
//         (x -> new VectorBuilder[WrappedEvent[T]])
//       }.toMap

//       Behaviors.receiveMessage {
//         case portals.system.async.Events.Subscribe(subscriber, replyTo) => ??? // not supported

//         case portals.system.async.Events.Event(sender, event) =>
//           event match
//             case Event(key, value) =>
//               // if this sender is not blocking
//               if blocking(sender) == 0 then
//                 subscribers.foreach { _ ! portals.system.async.Events.Event(path, event) }
//                 Behaviors.same
//               // if sender blocking, then buffer
//               else
//                 buffer(sender).addOne(event.asInstanceOf[WrappedEvent[T]])
//                 Behaviors.same
//             case Atom =>
//               // add atom to atoms, increment blocking, clear buffer
//               blocking += sender -> (blocking(sender) + 1)
//               atoms += sender -> (atoms(sender) :+ buffer(sender).result())
//               buffer(sender).clear()

//               // if all blocking, then we can release one atom
//               if blocking.values.forall(_ > 0) then
//                 atoms.foreach { (_, atomsvec) =>
//                   val atom = atomsvec.head
//                   subscribers.foreach { sub => atom.foreach { e => sub ! portals.system.async.Events.Event(path, e) } }
//                 }
//                 subscribers.foreach { sub => sub ! portals.system.async.Events.Event(path, Atom) }
//                 atoms = atoms.mapValues { _.tail }.toMap
//                 blocking = blocking.mapValues { _ - 1 }.toMap
//               Behaviors.same

//             case Seal =>
//               seald += sender
//               // then we know it is fully seald, we also know that we have sent all previous atoms, as no more atoms are coming
//               if seald.size == senders.size then
//                 subscribers.foreach { _ ! portals.system.async.Events.Event(path, Seal) }
//                 Behaviors.stopped
//               else Behaviors.same
//             case Error(t) => ???
//       }
//     }

// trait PartitionMap[K: Ordering, V] {
//   def get(key: K): Option[V]

//   def getPartitionFromKey(key: K): Option[Partition[K]]

//   def getPartitionFromValue(value: V): Option[Partition[K]]

//   def values(): List[V]
// }

// import Ordering.Implicits._

// // the partition range is inclusive, i.e. `from` and `to` are included in the partition.
// case class Partition[K: Ordering](from: K, to: K) {
//   def contains(i: K): Boolean =
//     (i >= from) && (i <= to)

//   def intersection(that: Partition[K]): Option[Partition[K]] =
//     val intersection = Partition(
//       from = if this.from < that.from then that.from else this.from,
//       to = if this.to > that.to then that.to else this.to,
//     )
//     if (intersection.from <= intersection.to) then Some(intersection)
//     else None
// }

// class PartitionMapImpl[V](vs: V*) extends PartitionMap[Int, V] {
//   private val min: Long = Int.MinValue.toLong
//   private val max: Long = Int.MaxValue.toLong
//   private val transformedMax: Long = max - min

//   private val length: Long = vs.length // number of partitions

//   private def getIndex(i: Int): Int =
//     // values are transformed to be positive and in the range: [0, transformedMax]
//     // by subtracting `min` from the value, this simplifies calculating the
//     // correct partition
//     (((i.toLong - min) * length) / (transformedMax + 1)).toInt

//   private def getPartitionFromIndex(idx: Long): Partition[Int] =
//     // we add `min` to transform back to unnormalized values
//     val from = ((transformedMax + 1) * idx) / length + min
//     // subtract 1 as to not overlap with next partition
//     val to = ((transformedMax + 1) * (idx + 1)) / length + min - 1
//     Partition[Int](
//       from = from.toInt,
//       to = to.toInt,
//     )

//   override def get(i: Int): Option[V] =
//     Some(vs(getIndex(i)))

//   override def getPartitionFromKey(i: Int): Option[Partition[Int]] =
//     val idx = getIndex(i).toLong
//     Some(getPartitionFromIndex(idx))

//   override def getPartitionFromValue(v: V): Option[Partition[Int]] =
//     vs.indexOf(v) match
//       case -1 => None // indexOf returns `-1` if t is not in ts
//       case x => Some(getPartitionFromIndex(x))

//   override def values(): List[V] = vs.toList
// }

 */
