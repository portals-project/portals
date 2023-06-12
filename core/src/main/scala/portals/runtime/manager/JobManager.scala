package portals.runtime.manager

import java.io.File
import java.net.URLClassLoader

import portals.application.Application
import portals.application.AtomicPortalRef
import portals.runtime.manager.SubmittableApplication
import portals.system.InterpreterSystem
import portals.system.Systems
import portals.util.Logger

import cask.Request

object JobManager {
  val system: InterpreterSystem = Systems.interpreter()

  def stepUntilComplete: Unit = {
    system.stepUntilComplete()
  }

  def submitApplication(submittableApplication: SubmittableApplication): Unit = {
    val app = submittableApplication.application
    system.launch(app)
    if (system.runtime.rctx.applications.size > 1) {
      submittableApplication.portalStubs.foreach(portalSub => {
        rewireStubPortal(portalSub.portal, portalSub.targetApplication)
        // TODO: stub portals are also registered, but are not useful anymore, need to be cleared from app ctx
      })
    }
    println("submitted " + app.path)
  }

  /** Points path for {@link PortalStub} to the actual application
    */
  def rewireStubPortal(portal: AtomicPortalRef[_, _], dstApplication: String): Unit = {
    val oldPath = portal.path
    val oldPathSplit = oldPath.split("/")
    oldPathSplit(1) = dstApplication
    val actualPath = oldPathSplit.mkString("/")

    val pathField = portal.getClass.getDeclaredField("path")
    pathField.setAccessible(true)
    pathField.set(portal, actualPath)
    pathField.setAccessible(false)

    system.runtime.rctx._portals = system.runtime.rctx._portals - oldPath

    println(s"rewired stub portal $oldPath to $actualPath")
  }
}

object Server extends cask.MainRoutes {

  // NOTE: classes with same fully-qualified name loaded by different classLoader instance are not the same type
  // Warning: this chain may go to long if we submit too many applications
  var currentClassLoader = Server.getClass.getClassLoader

  @cask.get("/step")
  def step() = {
    JobManager.stepUntilComplete
    "ok"
  }

  /** submit "/path/to/clsFile/root com.package.yourApplicationClass" */
  @cask.post("/submitUrl")
  def submitUrl(request: cask.Request) = {
    val urlCls = request.text().split(" ");
    println("body: " + request.text())
    val url = urlCls(0)
    val clsName = urlCls(1)

    val f = new File(url)
    // https://stackoverflow.com/a/3941480/8454039  Array equals []
    currentClassLoader = new URLClassLoader(Array(f.toURI.toURL), currentClassLoader)
    val cls = Class.forName(clsName, true, currentClassLoader)
    val submittableApplication = cls.getConstructor().newInstance().asInstanceOf[SubmittableApplication]

    println("loaded " + cls.getName)

    JobManager.submitApplication(submittableApplication)
    "ok"
  }

  initialize()
}
