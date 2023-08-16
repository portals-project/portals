// package portals.distributed.remote.examples

// import scala.util.Failure
// import scala.util.Success
// import scala.util.Try

// import portals.api.builder.*
// import portals.api.dsl.DSL.*
// import portals.api.dsl.ExperimentalDSL.*
// import portals.application.*
// import portals.application.task.*
// import portals.distributed.remote.RemoteExtensions.*
// import portals.distributed.server.SBTRunServer
// import portals.distributed.server.Server
// import portals.system.Systems
// import portals.util.Future

// import upickle.default.*

// // object Requester extends App:
// //   private def app: Application =
// //     PortalsApp("AccessFromRemote"):

// //       // THE REMOTE PORTAL THAT WE WANT TO CONNECT TO :))
// //       // SERVICE THAT REVERSES STRINGS FOR US :)
// //       val remotePortal =
// //         RemoteRegistry.portals.get[String, String](
// //           "127.0.0.1:8080",
// //           "/reverse/portals/reverse",
// //         )

// //       // WE WANT TO SEND TO THE REMOTE PORTAL FROM OUR LOCAL WORKFLOW
// //       val generator = Generators.fromList(List("HelloWorld", "Sophia", "Jonas"))

// //       val workflow = Workflows[String, String]("AccessFromRemote")
// //         .source(generator.stream)
// //         .map(_.toUpperCase)
// //         .asker(remotePortal) { //
// //           case x =>
// //             ask(remotePortal)(x)
// //               .onComplete {
// //                 case Success(value) => ctx.emit(value)
// //                 case Failure(exception) => ctx.emit(exception.getMessage)
// //               }
// //         }
// //         .logger()
// //         .sink()
// //         .freeze()

// //   val system = Systems.remote()
// //   system.launch(app)
// //   system.stepUntilComplete()
// //   system.shutdown()
