package portals.distributed.remote

import scala.util.*

import portals.api.builder.*
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.application.task.*
import portals.system.Systems
import portals.util.Future

object RemoteExtensions:
  import RemoteShared.*

  def RemoteRegistry(using ApplicationBuilder) = RemoteRegistryBuilder

  case class RemotePortalRef[T, R](path: String) extends AtomicPortalRefKind[T, R]

  trait RemoteRegistry2[A[_, _]]:
    /** Get a reference of `path` at `url` from the registry. */
    def get[T, U](url: String, path: String)(using ApplicationBuilder): A[T, U]
  end RemoteRegistry2

  object PortalRemoteRegistryImpl extends RemoteRegistry2[RemotePortalRef]:
    def get[T, R](url: String, path: String)(using builder: ApplicationBuilder): RemotePortalRef[T, R] =
      RemotePortalRef(REMOTE_PATH(url, path))

  object RemoteRegistryBuilder:
    def portals: RemoteRegistry2[RemotePortalRef] =
      PortalRemoteRegistryImpl
  end RemoteRegistryBuilder

  extension [Rep](future: Future[Rep]) {
    def onComplete[T, U, Req](f: (Try[Rep]) => Unit)(using AskerTaskContext[T, U, Req, Rep]): Unit =
      await(future):
        val tried = Try(future.value.get) match
          case Success(value) => Success(value)
          case Failure(exception) => Failure(exception)
        f(tried)
  }

  extension (systems: Systems) {
    def remote(): RemoteSystem =
      new RemoteSystem()
  }
