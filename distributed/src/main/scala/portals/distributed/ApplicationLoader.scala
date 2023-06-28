package portals.distributed

import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files

import portals.distributed.Events.ClassFileInfo

/** Dynamically load and submit applications using the `ApplicationLoader`.
  *
  * Important: the submitted application must be either a class or object. For
  * this reason, the submitted application must extend the `LoadableApplication`
  * trait.
  */
object ApplicationLoader:
  /** Get some class `name` of any type of object `obj` if it exists. */
  def getClassName(obj: Any): String =
    val className = obj.getClass.getName
    className

  /** Get some `classFile`s name of any type of object `obj` if it exists.
    *
    * Note: this method returns the relative path to the class file constructed
    * from the class name.
    */
  def getClassFileName(obj: Any): String =
    val className = getClassName(obj)
    val classFileName = className.replace('.', '/') + ".class"
    classFileName

  /** Get the class file name for the submitted object `obj` from the submitted
    * directory `dir`.
    */
  def getClassFileNameFromDir(obj: Any, dir: String): Option[String] =
    val cl = URLClassLoader(Array(URL("file://" + dir)))
    Some(ApplicationLoader.getClassFileName(obj))

  /** Get the classfiles directory for the submitted object `obj`. */
  def getClassFileDirectory(obj: Any): String =
    obj.getClass.getProtectionDomain().getCodeSource().getLocation().getPath()

  /** Load a class from a name. */
  def loadClassFromName(name: String): Class[_] =
    PortalsClassLoader.loadClass(name)

  /** Create an instance of a class from a class. */
  def createInstanceFromClass(clazz: Class[_]): Any =
    val constructor = clazz.getDeclaredConstructor()
    constructor.setAccessible(true)
    constructor.newInstance()

  // temporary directory in Java for class files
  private val tmpDir = Files.createTempDirectory("application-loader")

  /** A class loader for loading class files from a temporary directory.
    *
    * Note: this is a singleton object.
    */
  object PortalsClassLoader extends URLClassLoader(Array(tmpDir.toUri.toURL), Server.getClass.getClassLoader):
    private[portals] def addClassFile(cfi: ClassFileInfo): Unit =
      val path = cfi.path
      val bytes = cfi.bytes

      // create directories of path if they don't already exist in the tmpDir
      val dirs = path.split("/").dropRight(1)
      dirs.foldLeft(tmpDir) { (dir, subDir) =>
        val newDir = dir.resolve(subDir)
        if !Files.exists(newDir) then Files.createDirectory(newDir)
        newDir
      }

      // write class file to tmpDir
      val file = tmpDir.resolve(path).toFile
      Files.write(file.toPath, bytes)

end ApplicationLoader
