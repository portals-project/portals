package portals.distributed

import mainargs.ParserForMethods

/** Command line interface for the client.
  *
  * Note: to run it from SBT use (don't forget to end with a "):
  * ```
  * sbt "distributed/runMain portals.distributed.ClientCLI ..."
  * ```
  */
object ClientCLI:

  /** Submit a classfile to the server.
    *
    * @example
    *   {{{
    * ClientCLI submit --path portals/distributed/examples/HelloWorld$.class
    *   }}}
    *
    * @example
    *   {{{
    * ClientCLI submit --path portals/distributed/examples/HelloWorld$.class --directory distributed/target/scala-3.3.0/classes
    *   }}}
    */
  @mainargs.main
  def submit(
      path: String,
      directory: String = "."
  ): Unit =
    Client.submitClassFile(path, directory)

  /** Submit all classfiles within a `directory` to the server.
    *
    * @example
    *   {{{
    * ClientCLI submitDir --directory distributed/target/scala-3.3.0/classes
    *   }}}
    */
  @mainargs.main
  def submitDir(
      directory: String
  ): Unit =
    Client.submitClassFilesFromDir(directory)

  /** Launch an application on the server.
    *
    * @example
    *   {{{
    * ClientCLI launch --application portals.distributed.examples.HelloWorld$
    *   }}}
    */
  @mainargs.main
  def launch(
      application: String,
  ): Unit =
    Client.launch(application)

  // using the mainargs library
  def main(args: Array[String]): Unit =
    ParserForMethods(this).runOrExit(args.toSeq)
