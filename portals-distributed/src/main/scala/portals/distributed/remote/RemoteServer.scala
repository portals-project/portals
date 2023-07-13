package portals.distributed.remote

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import portals.api.builder.*
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.application.task.*
import portals.application.Application
import portals.application.AtomicStreamRefKind
import portals.distributed.server.*
import portals.distributed.server.ApplicationLoader.*
import portals.distributed.server.Events.*
import portals.distributed.server.SBTRunServer
import portals.distributed.server.Server
import portals.system.Systems
import portals.util.Future

import upickle.default.*

object RemoteServer extends cask.MainRoutes:
  import RemoteShared.*

  /** Handle a `SubmitClassFiles` event. Submits class files to the server. */
  @cask.post("/submitClassFiles")
  def submitClassFiles(request: cask.Request) =
    val bytes = request.readAllBytes()
    read[SubmitClassFiles](bytes) match
      case SubmitClassFiles(classFiles) =>
        classFiles.foreach:
          case cfi @ ClassFileInfo(_, _) =>
            PortalsClassLoader.addClassFile(cfi)
    cask.Response("success", statusCode = 200)

  /** Handle a `Launch` event. Launches an application together with all of its
    * dependencies.
    */
  @cask.post("/launch")
  def launch(request: cask.Request) =
    val bytes = request.readAllBytes()
    read[Launch](bytes) match
      case Launch(app) =>
        val clazz = ApplicationLoader.loadClassFromName(app)
        val application = ApplicationLoader.createInstanceFromClass(clazz).asInstanceOf[SubmittableApplication].apply()
        // ASTPrinter.println(application)
        RemoteServerRuntime.launch(application)
    cask.Response("success", statusCode = 200)

  /** Handle a `PortalRequest` remote event. */
  @cask.post("/remoteReq")
  def remoteReq(request: cask.Request) =
    val bytes = request.readAllBytes()
    val event = read[PortalRequest](bytes)
    val response = event match
      case PortalRequest(batch) =>
        // ASK_2
        val b = batch.map(x => TRANSFORM_ASK_2(x))
        RemoteServerRuntime.feed(b)
    cask.Response("success", statusCode = 200)

  @cask.post("/remoteRes")
  def remoteRes(request: cask.Request) =
    val bytes = request.readAllBytes()
    val event = read[PortalResponse](bytes)
    val response = event match
      case PortalResponse(batch) =>
        RemoteServerRuntime.feed(batch)
    cask.Response("success", statusCode = 200)

  initialize()
