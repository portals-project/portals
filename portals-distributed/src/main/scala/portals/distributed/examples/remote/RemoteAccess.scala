package portals.distributed.examples

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import portals.api.builder.*
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.application.task.*
import portals.distributed.SBTRunServer
import portals.distributed.Server
import portals.system.Systems
import portals.util.Future

import upickle.default.*

object RemoteShared:
  sealed case class PortalRequest(url: String, path: String, event: String) derives ReadWriter
  sealed case class PortalResponse(event: String) derives ReadWriter

object RemoteServer extends cask.MainRoutes:
  import RemoteShared.*

  @cask.post("/remote")
  def remote(request: cask.Request) =
    println(request)
    val bytes = request.readAllBytes()
    val event = read[PortalRequest](bytes)
    println(event)
    val response = event match
      case PortalRequest(url, path, event) =>
        val resp = PortalResponse(event.reverse)
        val bytes = write(resp).getBytes()
        bytes
    cask.Response(response, statusCode = 200)

  initialize()

object RemoteSBTRunServer extends cask.Main:
  // define the routes which this server handles
  val allRoutes = Seq(RemoteServer)

  // execute the main method of this server
  this.main(args = Array.empty)

  // sleep so that we don't exit prematurely
  Thread.sleep(Long.MaxValue)

  // exit before running this servers main method (again)
  System.exit(0)

object RemoteClient:
  import RemoteShared.*

  /** Post the Launch `event` to the server. */
  def postToPortal(event: PortalRequest): String =
    val bytes = write(event).getBytes()
    val response = requests.post("http://localhost:8080/remote", data = bytes)
    response match
      case r if r.statusCode == 200 =>
        val resp = read[PortalResponse](r.bytes)
        resp.event
      case r => ???

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
