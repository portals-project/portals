package portals.distributed.remote.examples

import scala.util.*

import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.distributed.remote.*
import portals.distributed.remote.RemoteExtensions.*
import portals.distributed.server.*

// /CHATGPT
def numberToString(n: Int): String = {
  val units = Array(
    "zero",
    "one",
    "two",
    "three",
    "four",
    "five",
    "six",
    "seven",
    "eight",
    "nine",
    "ten",
    "eleven",
    "twelve",
    "thirteen",
    "fourteen",
    "fifteen",
    "sixteen",
    "seventeen",
    "eighteen",
    "nineteen"
  )
  val tens = Array("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

  if (n < 20) units(n)
  else if (n < 100) tens(n / 10) + (if (n % 10 > 0) " " + units(n % 10) else "")
  else if (n < 1000) units(n / 100) + " hundred" + (if (n % 100 > 0) " and " + numberToString(n % 100) else "")
  else if (n <= 9999) units(n / 1000) + " thousand" + (if (n % 1000 > 0) " " + numberToString(n % 1000) else "")
  else "number out of range"
}

object Requester extends SubmittableApplication:
  def apply(): Application =
    PortalsApp("Requester"):

      val remotePortal = RemoteRegistry.portals
        .get[String, String](
          "http://localhost:8081",
          "/Replier/portals/reverse",
        )

      // val generator = Generators.fromList(List("HelloWorld", "Sophia", "Jonas"))
      val generator = Generators.fromRange(0, 1024 * 1024, 1)

      // FIXME: breaks if not named, due to conflict with dollar symbol.
      val workflow = Workflows[Int, String]("asjdasdfkj")
        .source(generator.stream)
        .map(x => numberToString(x))
        .asker(remotePortal): //
          x =>
            ask(remotePortal)(x)
              .onComplete {
                case Success(value) => ctx.emit(value)
                case Failure(exception) => ctx.emit(exception.getMessage)
              }
        .logger()
        .sink()
        .freeze()

object RequesterClient extends App:
  val port = "8082"
  Client.port = port
  RemoteServerRuntime.system.url = s"http://localhost:$port"
  RemoteSBTRunServer.main(Array(port))

  Client.launchObject(Requester)

  // sleep so that we don't exit prematurely
  Thread.sleep(Long.MaxValue)

  // exit before running this servers main method (again)
  System.exit(0)

object AKSJD extends App:
  ASTPrinter.println(Requester())
