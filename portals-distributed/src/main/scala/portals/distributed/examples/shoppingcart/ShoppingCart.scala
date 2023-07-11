package portals.distributed.examples.shoppingcart

import portals.distributed.Client

/** Incrementally launches a shopping cart application using the `Client`.
  *
  * Note: in order to run this example, a corresponding `Server` must be
  * running. @see [[portals.distributed.SBTRunServer]].
  *
  * Note: this will use the programmatic `Client` object interface to launch the
  * application.
  *
  * Optionally, to launch the application using the `ClientCLI` see the
  * following example:
  * ```
  * // comment out the files that you want to submit to the server (so that they are not compiled with the server.)
  *
  * // start the server (in a different terminal)
  * sbt "distributed/runMain portals.distributed.SBTRunServer"
  *
  * // uncomment the files you want to submit
  *
  * // submit the class files with the client
  * sbt "distributed/runMain portals.distributed.ClientCLI submitDir --directory portals-distributed/target/scala-3.3.0/classes"
  *
  * // launch each application with the client
  * sbt "distributed/runMain portals.distributed.ClientCLI launch --application portals.distributed.examples.shoppingcart.Inventory$"
  * sbt "distributed/runMain portals.distributed.ClientCLI launch --application portals.distributed.examples.shoppingcart.Cart$"
  * sbt "distributed/runMain portals.distributed.ClientCLI launch --application portals.distributed.examples.shoppingcart.Orders$"
  * sbt "distributed/runMain portals.distributed.ClientCLI launch --application portals.distributed.examples.shoppingcart.Analytics$"
  * ```
  */
object ShoppingCart extends App:
  // submit all the class files with the client
  Client.submitObjectWithDependencies(this)

  // launch the applications
  Client.launchObject(Inventory)
  Client.launchObject(Cart)
  Client.launchObject(Orders)
  Client.launchObject(Analytics)
