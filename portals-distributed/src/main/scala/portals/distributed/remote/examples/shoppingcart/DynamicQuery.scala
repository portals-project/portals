package portals.distributed.remote.examples.shoppingcart

import scala.util.Failure
import scala.util.Success

import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.distributed.*
import portals.distributed.examples.shoppingcart.*
import portals.distributed.remote.*
import portals.distributed.remote.RemoteExtensions.*
import portals.examples.shoppingcart.ShoppingCartEvents.*

/** A proxy for the shopping cart portals, to circumvent the restriction on the
  * request/reply type.
  *
  * @see
  *   [[portals.distributed.remote.examples.shoppingcart.DynamicQuery]]
  */
object ShoppingCartProxy extends SubmittableApplication:
  override def apply(): Application =
    // Note: we are restricted to only supporting requests/replies of type
    // `String` here at the moment, this is only a temporary restriction.
    PortalsApp("ProxyServices"):
      //////////////////////////////////////////////////////////////////////////
      // Analytics PROXY
      //////////////////////////////////////////////////////////////////////////

      // Note: we are restricted to only supporting requests/replies of type
      // `String` here at the moment, this is only a temporary restriction.
      // Here we use `Any` just temporarily.
      val anaProxy = Portal[Any, Any]("analytics")

      // The real analytics service
      val anaServc = Registry.portals.get[Any, Any]("/Analytics/portals/analytics")

      val _ = Workflows[Nothing, Nothing]()
        .source(Generators.empty[Nothing].stream)
        .askerreplier(anaServc)(anaProxy)(_ => ???): //
          // PROXY REQUEST Top100
          case "Top100" =>
            // Send the request "Top100" to the analytics service and reply with
            // the result
            ask(anaServc)(Top100).onComplete:
              case Success(r) =>
                reply(r.toString())
              case Failure(e) =>
                reply(s"Error: $e")
          // NO MATCH
          case unkwn =>
            reply(s"Error: unknown request: $unkwn")
        .empty[Nothing]()
        .sink()
        .freeze()

      //////////////////////////////////////////////////////////////////////////
      // INVENTORY PROXY
      //////////////////////////////////////////////////////////////////////////

      val invProxy = Portal[Any, Any]("inventory")

      val invServc = Registry.portals.get[Any, Any]("/Inventory/portals/inventory")

      val _ = Workflows[Nothing, Nothing]()
        .source(Generators.empty[Nothing].stream)
        .askerreplier(invServc)(invProxy)(_ => ???): //
          // PROXY REQUEST <GET, INT>
          case s: String if s.startsWith("Get") =>
            val splits = s.split(" ")
            val item = splits(1).toInt
            ask(invServc)(Get(item)).onComplete:
              case Success(r) =>
                reply(r.toString())
              case Failure(e) =>
                reply(s"Error: $e")
          // PROXY REQUEST <PUT, INT>
          case s: String if s.startsWith("Put") =>
            val splits = s.split(" ")
            val item = splits(1).toInt
            ask(invServc)(Put(item)).onComplete:
              case Success(r) =>
                reply(r.toString())
              case Failure(e) =>
                reply(s"Error: $e")
          // NO MATCH
          case unkwn =>
            reply(s"Error: unknown request: $unkwn")
        .empty[Nothing]()
        .sink()
        .freeze()

/** Example showcasing the dynamic remote composition using remote Portals.
  *
  * This example uses the `Remote` extension, a current work in progress, to
  * send dynamic queries to the shopping cart app.
  *
  * To run the example:
  *   - Option 1: start the server (in a different terminal) on localhost 8080,
  *     and launch the shopping cart app, and run the main method of this class.
  *   - Option 2: follow the instructions below. See the code examples for how
  *     to launch everything.
  *
  * **Note:** at the moment, this example is limited to queries of type
  * `String`, this is due to a restriction in the remote library for now.
  *
  * @example
  *   {{{
  * // start the server (in a different terminal)
  * sbt "distributed/runMain portals.distributed.remote.RemoteSBTRunServer localhost 8080"
  *
  * // start the shopping cart app
  * sbt "distributed/runMain portals.distributed.ClientCLI submitDir --directory portals-distributed/target/scala-3.3.0/classes --ip localhost --port 8080"
  * sbt "distributed/runMain portals.distributed.ClientCLI launch --application portals.distributed.examples.shoppingcart.Inventory$  --ip localhost --port 8080"
  * sbt "distributed/runMain portals.distributed.ClientCLI launch --application portals.distributed.examples.shoppingcart.Cart$  --ip localhost --port 8080"
  * sbt "distributed/runMain portals.distributed.ClientCLI launch --application portals.distributed.examples.shoppingcart.Orders$  --ip localhost --port 8080"
  * sbt "distributed/runMain portals.distributed.ClientCLI launch --application portals.distributed.examples.shoppingcart.Analytics$  --ip localhost --port 8080"
  * sbt "distributed/runMain portals.distributed.ClientCLI launch --application portals.distributed.remote.examples.shoppingcart.ShoppingCartProxy$ --ip localhost --port 8080"
  *
  * // start a new server for the dynamic query app
  * sbt "distributed/runMain portals.distributed.remote.RemoteSBTRunServer localhost 8081"
  *
  * // start the dynamic query app
  * sbt "distributed/runMain portals.distributed.ClientCLI submitDir --directory portals-distributed/target/scala-3.3.0/classes --ip localhost --port 8081"
  * sbt "distributed/runMain portals.distributed.ClientCLI launch --application portals.distributed.remote.examples.shoppingcart.DynamicQuery$ --ip localhost --port 8081"
  *   }}}
  */
object DynamicQuery extends SubmittableApplication:
  // CONFIG
  val host = "localhost"
  val port = "8081"
  val remoteHost = "localhost"
  val remotePort = "8080"

  // THE PORTALS APPLICATION
  override def apply(): Application =
    PortalsApp("DynamicQueryApp"):
      //////////////////////////////////////////////////////////////////////////
      // QUERY THE INVENTORY SERVICE
      //////////////////////////////////////////////////////////////////////////

      // A remote reference to the inventory service
      val inventory = RemoteRegistry.portals.get[String, String](
        s"http://$remoteHost:$remotePort",
        "/ProxyServices/portals/inventory",
      )

      // A stream with a single element to trigger the query
      val inventoryQueryStream = Generators.signal("Get 1").stream
      // val inventoryQueryStream = Generators.fromList[String](List("Put 1", "Get 1")).stream

      // The workflow which queries the services
      val _ = Workflows[String, String]()
        .source(inventoryQueryStream)
        .asker(inventory): x =>
          // Send the request to the inventory service
          ask(inventory)(x).onComplete:
            case Success(r) =>
              // Log and emit successfull results
              log.info(s"Got result: $r")
              emit(r)
            case Failure(e) =>
              log.error(s"Error: $e")
              emit(s"Error: $e")
        .sink()
        .freeze()

      //////////////////////////////////////////////////////////////////////////
      // QUERY THE ANALYTICS SERVICE
      //////////////////////////////////////////////////////////////////////////

      val analytics = RemoteRegistry.portals.get[String, String](
        s"http://$remoteHost:$remotePort",
        "/ProxyServices/portals/analytics",
      )

      // A stream with a single element to trigger the query
      val analyticsQueryStream = Generators.signal("Top100").stream

      // The workflow which queries the services
      val _ = Workflows[String, String]()
        .source(analyticsQueryStream)
        .asker(analytics): x =>
          // Send the request `x` to the analytics service
          ask(analytics)(x).onComplete:
            case Success(r) =>
              // Log and emit successfull results
              log.info(s"Got result: $r")
              emit(r)
            case Failure(e) =>
              log.error(s"Error: $e")
              emit(s"Error: $e")
        .sink()
        .freeze()

  // Launch the server and app using this main method
  def main(args: Array[String]): Unit =
    RemoteSBTRunServer.InternalRemoteSBTRunServer.main(Array(host, port))

    Client.submitObjectWithDependencies(this, host, port.toInt)

    Client.launchObject(this, host, port.toInt)

    Thread.sleep(Long.MaxValue)
