package portals.runtime.state

import java.io.File
import org.apache.commons.io.FileUtils
import org.rocksdb
import portals.util.Serializer 

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

object JavaSerializer extends Serializer {
  override def serialize[T](obj: T): Array[Byte] =
    val bytes = new ByteArrayOutputStream()
    val out = new ObjectOutputStream(bytes)
    out.writeObject(obj)
    out.close()
    bytes.toByteArray

  override def deserialize[T](bytes: Array[Byte]): T =
    val in = new ObjectInputStream(new ByteArrayInputStream(bytes))
    in.readObject().asInstanceOf[T]
}

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




  override def snapshot(): Snapshot =
    val checkpointEpoch = (System.currentTimeMillis() % Int.MaxValue).toInt
    customRocksDB.checkpoint(checkpointEpoch)
    val snapshotRocksDB = CustomRocksDB(customRocksDB.checkpointdir(checkpointEpoch))
    new Snapshot {
      override def iterator: Iterator[(Any, Any)] = 
        val it = snapshotRocksDB.getRocksDB().newIterator()
        new Iterator[(Any, Any)] {
          override def hasNext: Boolean = {
            it.isValid
          }

          override def next(): (Any, Any) = {
            val key = JavaSerializer.deserialize[Any](it.key())
            val value = JavaSerializer.deserialize[Any](it.value())
            it.next()
            (key, value)
          }
        }

      // Call this method to clean up the snapshot resources when you're done with it
      def close(): Unit = {
        snapshotRocksDB.getRocksDB().close()
        FileUtils.deleteDirectory(File(snapshotRocksDB.getPath()))
      }
    }

  override def incrementalSnapshot(): Snapshot = 
    // Implement incremental snapshot if needed, otherwise leave as it is
    new Snapshot { def iterator = Iterator.empty }


object CustomRocksDB {
  def apply(): CustomRocksDB =
    val dbFile: String = File.createTempFile("tmpxxx", "").getAbsolutePath + "portals-db"
    new CustomRocksDB(dbFile)
  def apply(path: String): CustomRocksDB = new CustomRocksDB(path)
}

class CustomRocksDB(path: String) {
  File(path).mkdirs()

  rocksdb.RocksDB.loadLibrary()

  def getPath(): String = path

  def getRocksDB(): rocksdb.RocksDB = rocksDB

  private val options = new rocksdb.Options()
  options.setCreateIfMissing(true)

  def checkpointdir(epoch: Int): String = path + "/" + epoch.toString

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

  def checkpoint(epoch: Int): Unit =
    val checkpointdir = this.checkpointdir(epoch)
    val checkpoint: rocksdb.Checkpoint = rocksdb.Checkpoint.create(rocksDB);
    checkpoint.createCheckpoint(checkpointdir)

  def recover(epoch: Int): Unit =
    rocksDB.close()
    val checkpointdir = this.checkpointdir(epoch)
    FileUtils.deleteDirectory(File(livedir()))
    FileUtils.copyDirectory(File(checkpointdir), File(livedir()))
    rocksDB = rocksdb.RocksDB.open(options, livedir())
}