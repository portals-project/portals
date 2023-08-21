package portals.distributed.remote

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

object RemoteClient:
  import RemoteShared.*

  /** Post the Launch `event` to the server. */
  def postToPortalReq(url: String = "http://localhost:8080", event: PortalRequest): Unit =
    try
      val bytes = write(event).getBytes()
      val reqURL = url + "/remoteReq"
      val response = requests.post(reqURL, data = bytes)
      response match
        case r if r.statusCode == 200 =>
        case r => () // ignore bad return
    catch
      case e: Exception =>
        println(e)
        () // ignore bad return
    // new Thread(new Runnable {
    //   override def run(): Unit =
    //     val bytes = write(event).getBytes()
    //     val reqURL = url + "/remoteReq"
    //     val response = requests.post(reqURL, data = bytes)
    //     response match
    //       case r if r.statusCode == 200 =>
    //       case r => ???
    // }).start()

  /** Post the Launch `event` to the server. */
  def postToPortalRes(url: String = "http://localhost:8080", event: PortalResponse): Unit =
    try
      val bytes = write(event).getBytes()
      val reqURL = url + "/remoteRes"
      val response = requests.post(reqURL, data = bytes)
      response match
        case r if r.statusCode == 200 =>
        case r => () // ignore bad return
    catch
      case e: Exception =>
        println(e)
        () // ignore bad return
    // new Thread(new Runnable {
    //   override def run(): Unit =
    //     val bytes = write(event).getBytes()
    //     val reqURL = url + "/remoteRes"
    //     val response = requests.post(reqURL, data = bytes)
    //     response match
    //       case r if r.statusCode == 200 =>
    //       case r => ???
    // }).start()
