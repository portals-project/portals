package portals.distributed.server

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarFile

import scala.jdk.CollectionConverters.*

/** Utility functions used in the scope of the distributed library. */
object Util:
  def getBytes(path: String): Array[Byte] =
    val file = File(path)
    val bytes = Files.readAllBytes(file.toPath())
    bytes

  def getBytes(path: String, directory: String): Array[Byte] =
    if directory.endsWith(".jar") then //
      getBytesFromJar(path, directory)
    else //
      getBytesFromFile(path, directory)

  def getBytesFromFile(path: String, directory: String): Array[Byte] =
    val file = File(directory, path)
    val bytes = Files.readAllBytes(file.toPath())
    bytes

  def getBytesFromJar(path: String, directory: String): Array[Byte] =
    val jarFile = JarFile(directory)
    val jarEntry = jarFile.getJarEntry(path)
    val inputStream = jarFile.getInputStream(jarEntry)
    val bytes = inputStream.readAllBytes()
    bytes

  def getBytesFromDirectory(directory: String): Seq[(String, Array[Byte])] =
    if directory.endsWith(".jar") then //
      getBytesFromDirectoryFromJar(directory)
    else //
      getBytesFromDirectoryFromDirectory(directory)

  def getBytesFromDirectoryFromDirectory(directory: String): Seq[(String, Array[Byte])] =
    val directoryPath = Paths.get(directory)
    val files = Files.walk(directoryPath).iterator().asScala.toSeq
    val classFiles = files.filter(_.toString.endsWith(".class"))
    val bytes = classFiles.map(Files.readAllBytes).toSeq
    val paths = classFiles.map(directoryPath.relativize(_).toString)
    paths.zip(bytes)

  def getBytesFromDirectoryFromJar(directory: String): Seq[(String, Array[Byte])] =
    val jarFile = JarFile(directory)
    val entries = jarFile.entries().asScala.toSeq
    val classFiles = entries.filter(_.getName.endsWith(".class"))
    val bytes = classFiles.map(jarFile.getInputStream(_).readAllBytes())
    val paths = classFiles.map(_.getName)
    paths.zip(bytes)
