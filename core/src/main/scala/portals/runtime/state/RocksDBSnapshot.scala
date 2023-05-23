package portals.runtime.state

import org.rocksdb.{RocksDB, Options}
import portals.util.JavaSerializer

class RocksDBSnapshot(epoch: Int) extends Snapshot {
  

  // Why is this not implemented:
  // The readonly works only if there is an existing and filled checkpoint. If there are no values in the checkpoint, then rocksDB will throw an exception.
  /*
  RocksDB.loadLibrary()
  private val options = new Options().setCreateIfMissing(true)
  private val rocksDB: RocksDB = RocksDB.openReadOnly(options, checkpointDir)
  */

  override def iterator: Iterator[(Any, Any)] = ???
  /*{
    val rocksIterator = rocksDB.newIterator()

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
  }*/
}
