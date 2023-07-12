package portals.distributed.examples

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import portals.api.builder.*
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.application.task.*
import portals.distributed.server.SBTRunServer
import portals.distributed.server.Server
import portals.system.Systems
import portals.util.Future

import upickle.default.*

object Extensions:
  import RemoteShared.*

  def RemoteRegistry(using ApplicationBuilder) = RemoteRegistryBuilder

  case class RemotePortalRef[T, R](url: String, path: String) extends AtomicPortalRefKind[T, R]

  trait RemoteRegistry2[A[_, _]]:
    /** Get a reference of `path` at `url` from the registry. */
    def get[T, U](url: String, path: String)(using ApplicationBuilder): A[T, U]
  end RemoteRegistry2

  object PortalRemoteRegistryImpl extends RemoteRegistry2[RemotePortalRef]:
    def get[T, R](url: String, path: String)(using builder: ApplicationBuilder): RemotePortalRef[T, R] =
      val portal = builder.portals.portal[T, R](path)
      builder
        .workflows[Nothing, Nothing]
        .source(Generators.empty.stream)
        .replier[Nothing, T, R](portal) { _ => () } { //
          case x =>
            val req = PortalRequest(url, path, x.toString())
            val rep = RemoteClient.postToPortal(req)
            ctx.reply(rep.asInstanceOf[R])
        }
        .empty[Nothing]()
        .sink()

      RemotePortalRef(url, portal.path)

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

object RemoteAccessApp extends App:
  import Extensions.*

  private def app: Application =
    PortalsApp("AccessFromRemote"):

      // THE REMOTE PORTAL THAT WE WANT TO CONNECT TO :))
      // SERVICE THAT REVERSES STRINGS FOR US :)
      val remotePortal =
        RemoteRegistry.portals.get[String, String](
          "127.0.0.1:8080",
          "/reverse/portals/reverse",
        )

      // WE WANT TO SEND TO THE REMOTE PORTAL FROM OUR LOCAL WORKFLOW
      val generator = Generators.fromList(List("HelloWorld", "Sophia", "Jonas"))

      val workflow = Workflows[String, String]("AccessFromRemote")
        .source(generator.stream)
        .map(_.toUpperCase)
        .asker(remotePortal) { //
          case x =>
            ask(remotePortal)(x)
              .onComplete {
                case Success(value) => ctx.emit(value)
                case Failure(exception) => ctx.emit(exception.getMessage)
              }
        }
        .logger()
        .sink()
        .freeze()

  val system = Systems.test()
  system.launch(app)
  system.stepUntilComplete()
  system.shutdown()
