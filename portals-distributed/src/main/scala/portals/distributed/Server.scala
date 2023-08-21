package portals.distributed

import java.io.File

import portals.application.*
import portals.application.Application
import portals.application.AtomicStreamRefKind
import portals.distributed.*
import portals.distributed.ApplicationLoader.*
import portals.distributed.Events.*
import portals.system.Systems

import upickle.default.*
import upickle.default.read

/** Portals Server for handling remote requests to submit and launch
  * applications.
  *
  * Note: if you intend to run this with `sbt run` then you should use
  * `SBTRunServer` instead.
  */
object Server extends cask.MainRoutes:
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
        ASTPrinter.println(application)
        ServerRuntime.launch(application)
    cask.Response("success", statusCode = 200)

  // initialize the server (cask.MainRoutes method)
  initialize()
