package portals.runtime.manager

import scala.collection.mutable.ListBuffer

import portals.application.Application
import portals.application.AtomicPortalRef

case class PortalStub(portal: AtomicPortalRef[_, _], targetApplication: String)

/** Application format that is allowed to submit incrementally */
abstract class SubmittableApplication {

  /** Remote portals that receives requests from this applications should be
    * placed in {@link portalStubs}
    */
  val portalStubs = ListBuffer[PortalStub]()

  def application: Application

  def registerPortalStub(portal: AtomicPortalRef[_, _], targetApplication: String) =
    portalStubs += PortalStub(portal, targetApplication)
}
