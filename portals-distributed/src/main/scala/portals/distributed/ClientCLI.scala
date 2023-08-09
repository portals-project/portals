package portals.distributed

import mainargs.ParserForMethods

/** Command line interface for the client.
  *
  * Note: to run it from SBT use (don't forget to end with a "):
  * ```
  * sbt "distributed/runMain portals.distributed.ClientCLI ..."
  * ```
  * @param path the path to the classfile to submit
  * @param directory the directory containing the classfile to submit
  * @param (optional) ip the IP address of the server
  * @param (optional) port the port of the server
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
    * ClientCLI submit --path portals/distributed/examples/HelloWorld$.class --directory portals-distributed/target/scala-3.3.0/classes
    *   }}}
    * 
    * @example
    *  {{{
    * ClientCLI submit --path portals/distributed/examples/HelloWorld$.class --ip localhost --port 8080
    *  }}}
    * 
    * @example
    * {{{
    * ClientCLI submit --path portals/distributed/examples/HelloWorld$.class --directory portals-distributed/target/scala-3.3.0/classes --ip localhost --port 8080
    * }}}
    */
  @mainargs.main
  def submit(
      path: String,
      directory: String = ".",
      ip: String = "localhost",
      port: Int = 8080
  ): Unit =
    Client.submitClassFile(path, directory, ip, port)

  /** Submit all classfiles within a `directory` to the server.
    *
    * @example
    *   {{{
    * ClientCLI submitDir --directory portals-distributed/target/scala-3.3.0/classes
    *   }}}
    * 
    * @example
    * {{{
    * ClientCLI submitDir --directory portals-distributed/target/scala-3.3.0/classes --ip localhost --port 8080
    * }}}
    */
  @mainargs.main
  def submitDir(
      directory: String,
      ip: String = "localhost",
      port: Int = 8080
  ): Unit =
    Client.submitClassFilesFromDir(directory)

  /** Launch an application on the server.
    *
    * @example
    *   {{{
    * ClientCLI launch --application portals.distributed.examples.HelloWorld$
    *   }}}
    * 
    * @example
    *  {{{
    * ClientCLI launch --application portals.distributed.examples.HelloWorld$ --ip localhost --port 8080
    *  }}}
    */
  @mainargs.main
  def launch(
      application: String,
      ip: String = "localhost",
      port: Int = 8080
  ): Unit =
    Client.launch(application, ip, port)

  // using the mainargs library
  def main(args: Array[String]): Unit =
    ParserForMethods(this).runOrExit(args.toSeq)