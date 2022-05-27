package pods.workflows.streams

/** XAtomic Reactive Streams
 *
 *  The XAtomic streams are exactly-once and atomic. They transport atoms and 
 *  allow for transactional processing (both optimistic and pessimistic). The 
 *  Subscriber can choose to execute atoms and events that are not yet 
 *  committed, in which case it must also be able to recover from an upstream 
 *  failure. It may also choose to only execute atoms that have been committed, 
 *  in which case it does not have to consider upstream failures.
*/

/** XAtomic Streams Factory */
object Streams