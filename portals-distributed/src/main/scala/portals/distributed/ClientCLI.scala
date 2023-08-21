package portals.distributed

import portals.distributed.Client

import mainargs.ParserForMethods

/** Command line interface for the client.
  *
  * @example
  *   Display the help message to see all available commands:
  *   {{{
  * sbt "distributed/runMain portals.distributed.ClientCLI --help"
  *   }}}
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
    *   {{{
    * ClientCLI submit --path portals/distributed/examples/HelloWorld$.class --ip localhost --port 8080
    *   }}}
    *
    * @example
    *   {{{
    * ClientCLI submit --path portals/distributed/examples/HelloWorld$.class --directory portals-distributed/target/scala-3.3.0/classes --ip localhost --port 8080
    *   }}}
    *
    * @param path
    *   the path to the classfile to submit
    * @param directory
    *   (optional) the directory containing the classfile to submit
    * @param ip
    *   (optional) the IP address of the server
    * @param port
    *   (optional) the port of the server
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
    *   {{{
    * ClientCLI submitDir --directory portals-distributed/target/scala-3.3.0/classes --ip localhost --port 8080
    *   }}}
    *
    * @param directory
    *   the directory containing the classfile to submit
    * @param ip
    *   (optional) the IP address of the server
    * @param port
    *   (optional) the port of the server
    */
  @mainargs.main
  def submitDir(
      directory: String,
      ip: String = "localhost",
      port: Int = 8080
  ): Unit =
    Client.submitClassFilesFromDir(directory, ip, port)

  /** Launch an application on the server.
    *
    * @example
    *   {{{
    * ClientCLI launch --application portals.distributed.examples.HelloWorld$
    *   }}}
    *
    * @example
    *   {{{
    * ClientCLI launch --application portals.distributed.examples.HelloWorld$ --ip localhost --port 8080
    *   }}}
    *
    * @param application
    *   the Java path to the application to launch
    * @param ip
    *   (optional) the IP address of the server
    * @param port
    *   (optional) the port of the server
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
