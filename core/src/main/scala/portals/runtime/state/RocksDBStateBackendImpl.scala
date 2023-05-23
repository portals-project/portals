package portals.runtime.state

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintWriter
import scala.io.Source
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.file.{Files, Path, Paths}
import org.apache.commons.io.FileUtils
import org.rocksdb
import org.rocksdb.{Checkpoint, RestoreOptions, RocksDB}
import portals.util.JavaSerializer

private[portals] class RocksDBStateBackendImpl extends StateBackend:
  private val customRocksDB = CustomRocksDB()
  override def get(key: Any): Option[Any] = customRocksDB.get(key)
  override def set(key: Any, value: Any): Unit = customRocksDB.set(key, value)
  override def del(key: Any): Unit = customRocksDB.del(key)
  override def clear(): Unit = customRocksDB.clear()

  override def iterator: Iterator[(Any, Any)] = {
    val rocksIterator = customRocksDB.getRocksDB().newIterator()

    new Iterator[(Any, Any)] {
      override def hasNext: Boolean = {
        rocksIterator.isValid
      }

      override def next(): (Any, Any) = {
        if (!hasNext) throw new NoSuchElementException("No more elements in the RocksDB")
        val key = JavaSerializer.deserialize[Any](rocksIterator.key())
        val value = JavaSerializer.deserialize[Any](rocksIterator.value())
        rocksIterator.next()
        (key, value)
      }
    }
  }

  override def snapshot(): Snapshot = {
    val checkpointEpoch = System.currentTimeMillis().toInt
    customRocksDB.checkpoint(checkpointEpoch)
    new RocksDBSnapshot(checkpointEpoch)
  }

  override def incrementalSnapshot(): Snapshot = ???
  // Implement incremental snapshot if needed, otherwise leave as it is
  // new Snapshot { def iterator = Iterator.empty }

object CustomRocksDB {
  def apply(): CustomRocksDB = {
    val dbFileName = "portals-db"
    val dbFilePath = System.getenv("DB_FILE_PATH") // read env variable

    val dbFile = new File(dbFileName)

    val dbAbsolutePath = dbFile.getAbsolutePath()

    val uniquePath = generateUniquePath(dbAbsolutePath)
    new CustomRocksDB(uniquePath)
  }

  private def generateUniquePath(basePath: String): String = {
    val timestamp = System.currentTimeMillis()
    val uniqueId = java.util.UUID.randomUUID().toString
    s"$basePath/$timestamp-$uniqueId"
  }
}

class CustomRocksDB(path: String) {
  File(path).mkdirs()

  rocksdb.RocksDB.loadLibrary()

  def getPath(): String = path

  def getRocksDB(): rocksdb.RocksDB = rocksDB

  private val options = new rocksdb.Options()
  options.setCreateIfMissing(true)
  options.setMaxOpenFiles(1000)

  def checkpointdir(epoch: Int): String = path + "/backup/" + epoch.toString

  private var rocksDB = rocksdb.RocksDB.open(options, livedir())

  def set[K, V](key: K, value: V): Unit =
    rocksDB.put(JavaSerializer.serialize(key), JavaSerializer.serialize(value))

  def del[K](key: K): Unit =
    rocksDB.delete(JavaSerializer.serialize(key))

  def get[K, V](key: K): Option[V] =
    val bytes = rocksDB.get(JavaSerializer.serialize(key))
    if bytes == null then None else Option(JavaSerializer.deserialize(bytes))

  def clear(): Unit =
    rocksDB.close()
    FileUtils.deleteDirectory(File(path))
    File(path).mkdirs()
    rocksDB = rocksdb.RocksDB.open(options, livedir())

  // directory with files for live store
  private def livedir(): String = path + "/" + "current"

  def checkpoint(epoch: Int): Unit = {
    val checkpointdir = this.checkpointdir(epoch)
    val checkpointDirFile = new File(checkpointdir)
    checkpointDirFile.mkdirs()




    // Create the checkpoint
    val checkpoint: Checkpoint = Checkpoint.create(this.getRocksDB())
    //checkpoint.createCheckpoint(checkpointdir)
    try {
      checkpoint.createCheckpoint(checkpointdir)
    } catch {
      case e: Exception => 
        if (e.getMessage().contains("Directory exists")) {
          // Directory exists
          //println("Directory exists")
          return
        }
        e.printStackTrace()
        return
    }
  }

  def restore(epoch: Int): Unit = {
    val checkpointdir = this.checkpointdir(epoch)
    //println(s"Restoring: $epoch from $checkpointdir")

    rocksDB.close()

    val dbPath = Paths.get(path)
    val checkpointPath = Paths.get(checkpointdir)

    // Delete existing DB files
    FileUtils.deleteDirectory(File(path))
    // Copy from checkpoint directory to DB directory
    FileUtils.copyDirectory(File(checkpointdir), File(path))


    rocksDB = RocksDB.open(options, path)
  }


}
