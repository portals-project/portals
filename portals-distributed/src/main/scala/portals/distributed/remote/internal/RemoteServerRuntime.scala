package portals.distributed.remote

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.Future

import portals.application.*
import portals.application.Application
import portals.application.AtomicStreamRefKind
import portals.distributed.remote.RemoteExtensions.*
import portals.distributed.remote.RemoteShared.TRANSFORM_BATCH
import portals.distributed.ApplicationLoader
import portals.distributed.ApplicationLoader.PortalsClassLoader
import portals.distributed.Events.*
import portals.runtime.BatchedEvents.EventBatch
import portals.system.Systems

import upickle.default.*
import upickle.default.read

/** Server runtime system used by the distributed server. New applications can
  * be submitted via the `launch` method. Note that the system automatically
  * starts upon initialization.
  */
object RemoteServerRuntime:
  private class Worker:
    private val queue = new ConcurrentLinkedQueue[() => Unit]()

    def submitJob(job: () => Unit): Unit =
      queue.offer(job)

    // start automatically on creation
    new Thread(new Runnable {
      override def run(): Unit =
        while (true) do
          Option(queue.poll()) match
            case Some(job) =>
              job()
            case None =>
              Thread.sleep(100)
    }).start()
  end Worker

  private def stepJob: () => Unit =
    () =>
      if system.canStep() then //
        system.stepUntilComplete(1024)
      Thread.sleep(100)
      worker.submitJob(stepJob)

  private def launchJob(application: Application): () => Unit =
    () => system.launch(application)

  // start system and worker
  val system = Systems.remote()
  private val worker = new Worker()
  worker.submitJob(stepJob)

  /** Launch an application on the server runtime. */
  def launch(application: Application): Unit =
    worker.submitJob(launchJob(application))

  /** Ingest remote events on the server runtime. */
  def feed(batch: List[EventBatch]): Unit =
    worker.submitJob(() =>
      // feed events
      system.feed(batch)
    )
