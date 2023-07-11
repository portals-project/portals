package portals.distributed

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile

import scala.jdk.CollectionConverters.*

import portals.distributed.ApplicationLoader.*
import portals.distributed.Events.*
import portals.distributed.Util.*

import upickle.default.*

/** Portals distributed `Client` for submitting applications to the `Server`. */
object Client extends App:
  //////////////////////////////////////////////////////////////////////////////
  // POST TO SERVER
  //////////////////////////////////////////////////////////////////////////////

  /** Post the Launch `event` to the server. */
  def postToServer(event: Launch): Unit =
    val bytes = write(event).getBytes()
    val response = requests.post("http://localhost:8080/launch", data = bytes)
    response match
      case r if r.statusCode == 200 => println("success")
      case r => println(s"error: ${r.statusCode}")

  /** Post the SubmitClassFiles `event` to the server. */
  def postToServer(event: SubmitClassFiles): Unit =
    val bytes = write(event).getBytes()
    val response = requests.post("http://localhost:8080/submitClassFiles", data = bytes)
    response match
      case r if r.statusCode == 200 => println("success")
      case r => println(s"error: ${r.statusCode}")

  //////////////////////////////////////////////////////////////////////////////
  // CLI API
  //////////////////////////////////////////////////////////////////////////////

  /** Submit a classfile at `path` within `directory` to the server. */
  def submitClassFile(path: String, directory: String): Unit =
    val bytes = Util.getBytes(path, directory)
    val event = SubmitClassFiles(Seq(ClassFileInfo(path, bytes)))
    postToServer(event)

  /** Submit all classfiles within a `directory` to the server. */
  def submitClassFilesFromDir(directory: String): Unit =
    val dir = Paths.get(directory)
    val files = Files
      .walk(dir)
      .filter(_.toString.endsWith(".class"))
    val cfs = files
      .map { file =>
        val path = dir.relativize(file)
        val bytes = Files.readAllBytes(file)
        ClassFileInfo(path.toString, bytes)
      }
      .iterator()
      .asScala
      .toSeq
    val event = SubmitClassFiles(cfs)
    postToServer(event)

  /** Launch an `application` specified by its Java path to the server. */
  def launch(application: String): Unit =
    val event = Launch(application)
    postToServer(event)

  //////////////////////////////////////////////////////////////////////////////
  // OBJECT API
  //////////////////////////////////////////////////////////////////////////////

  /** Submit object to the server.
    *
    * @example
    *   {{{
    * object TestClass
    * submitObject(TestClass)
    *   }}}
    */
  def submitObject(obj: AnyRef): Unit =
    val path = ApplicationLoader.getClassFileName(obj)
    val dir = ApplicationLoader.getClassFileDirectory(obj)
    submitClassFile(path, dir)

  /** Submit object to the server.
    *
    * @example
    *   {{{
    * object TestClass
    * submitObject(TestClass)
    *   }}}
    */
  def submitObject(obj: AnyRef, classFilesDirectory: String): Unit =
    val path = ApplicationLoader.getClassFileName(obj)
    submitClassFile(path, classFilesDirectory)

  /** Submit an object with all its dependencies to the server.
    *
    * NOTE: this is not stable.
    */
  def submitObjectWithDependencies(obj: AnyRef): Unit =
    val dir = ApplicationLoader.getClassFileDirectory(obj)
    this.submitClassFilesFromDir(dir)

  /** Submit an object with all its dependencies to the server.
    *
    * NOTE: this is not stable.
    */
  def submitObjectWithDependencies(obj: AnyRef, classFilesDirectory: String): Unit =
    this.submitClassFilesFromDir(classFilesDirectory)

  /** Launch an `app` to the server. */
  def launchObject(app: AnyRef): Unit =
    val name = ApplicationLoader.getClassName(app)
    launch(name)
