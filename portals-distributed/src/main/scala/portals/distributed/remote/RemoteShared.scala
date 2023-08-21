package portals.distributed.remote

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import portals.api.builder.*
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.application.task.*
import portals.distributed.SBTRunServer
import portals.distributed.Server
import portals.runtime.BatchedEvents.*
import portals.runtime.WrappedEvents.*
import portals.system.Systems
import portals.util.Future
import portals.util.Key

import upickle.default.*

object RemoteShared:
  //////////////////////////////////////////////////////////////////////////////
  // REMOTE EVENTS
  /////////////////////////////////////////////////////////////////////////////

  /** A remote Portal Request event. The URL of the requesting deployment is in
    * the prefix of the portal path.
    */
  sealed case class PortalRequest(batch: List[AskBatch[String]]) derives ReadWriter

  /** A remote Portal Response event. The URL of the replying deployment is in
    * the prefix of the portal path.
    */
  sealed case class PortalResponse(batch: List[ReplyBatch[String]]) derives ReadWriter

  //////////////////////////////////////////////////////////////////////////////
  // EVENT SERIALIZATION
  //////////////////////////////////////////////////////////////////////////////

  // The following serialization scheme is used for the upickle library, and
  // serializes PortalRequests and PortalResponses to/from some binary
  // representation.
  // To extend this to cover all event types, it would require to add
  // `Reader`s and `Writer`s for these additional types.
  // NOTE: The current scheme only serializes the `String` type for
  // PortalRequests and PortalResponses. FIXME.

  given optionReadWriter[T: Reader: Writer]: ReadWriter[Option[T]] =
    readwriter[ujson.Value].bimap[Option[T]](
      opt =>
        opt match
          case Some(value) =>
            write[T](value)
          case None =>
            ujson.Null
      ,
      json =>
        json match
          case ujson.Null =>
            None
          case _ =>
            Some(read[T](json.str))
    )

  given metaReadWriter: ReadWriter[AskReplyMeta] =
    readwriter[ujson.Value].bimap[AskReplyMeta](
      meta =>
        ujson.Obj(
          "portal" -> write[String](meta.portal),
          "askingWF" -> write[String](meta.askingWF),
          "replyingWF" -> write[Option[String]](meta.replyingWF),
          "replyingTask" -> write[Option[String]](meta.replyingTask),
        ),
      json =>
        val obj = json.obj
        val portal = read[String](obj("portal").str)
        val askingWF = read[String](obj("askingWF").str)
        val replyingWF = read[Option[String]](obj("replyingWF").str)
        val replyingTask = read[Option[String]](obj("replyingTask").str)
        AskReplyMeta(portal, askingWF, replyingWF, replyingTask)
    )
  end metaReadWriter

  given keyReadWriter: ReadWriter[Key] =
    readwriter[ujson.Value].bimap[Key](
      key =>
        ujson.Obj(
          "key" -> write(key.x),
        ),
      json =>
        val obj = json.obj
        val key = read[Long](obj("key").str)
        Key(key)
    )

  given portalMetaReadWriter: ReadWriter[PortalMeta] =
    readwriter[ujson.Value].bimap[PortalMeta](
      meta =>
        ujson.Obj(
          "portal" -> write(meta.portal),
          "askingTask" -> write(meta.askingTask),
          "askingKey" -> write(meta.askingKey),
          "id" -> write(meta.id),
          "askingWF" -> write(meta.askingWF),
        ),
      json =>
        val obj = json.obj
        val portal = read[String](obj("portal").str)
        val askingTask = read[String](obj("askingTask").str)
        val askingKey = read[Key](obj("askingKey").str)
        val id = read[Int](obj("id").str)
        val askingWF = read[String](obj("askingWF").str)
        PortalMeta(portal, askingTask, askingKey, id, askingWF)
    )

  given askReadWRiter[T: Reader: Writer]: ReadWriter[Ask[T]] =
    readwriter[ujson.Value].bimap[Ask[T]](
      ask =>
        ujson.Obj(
          "key" -> write(ask.key),
          "meta" -> write(ask.meta),
          "event" -> write(ask.event),
        ),
      json =>
        val obj = json.obj
        val key = read[Key](obj("key").str)
        val meta = read[PortalMeta](obj("meta").str)
        val event = read[T](obj("event").str)
        Ask(key, meta, event)
    )

  given replyReadWriter[T: Reader: Writer]: ReadWriter[Reply[T]] =
    readwriter[ujson.Value].bimap[Reply[T]](
      reply =>
        ujson.Obj(
          "key" -> write(reply.key),
          "meta" -> write(reply.meta),
          "event" -> write(reply.event),
        ),
      json =>
        val obj = json.obj
        val key = read[Key](obj("key").str)
        val meta = read[PortalMeta](obj("meta").str)
        val event = read[T](obj("event").str)
        Reply(key, meta, event)
    )

  given askBatchReadWRiter[T: Reader: Writer]: ReadWriter[AskBatch[T]] =
    readwriter[ujson.Value].bimap[AskBatch[T]](
      batch =>
        ujson.Obj(
          "meta" -> write(batch.meta),
          "list" -> write(batch.list.asInstanceOf[List[Ask[T]]])
        ),
      json =>
        val obj = json.obj
        val meta = read[AskReplyMeta](obj("meta").str)
        val list = read[List[Ask[T]]](obj("list").str)
        AskBatch(meta, list)
    )

  given replyBatchReadWRiter[T: Reader: Writer]: ReadWriter[ReplyBatch[T]] =
    readwriter[ujson.Value].bimap[ReplyBatch[T]](
      batch =>
        ujson.Obj(
          "meta" -> write(batch.meta),
          "list" -> write(batch.list.asInstanceOf[List[Reply[T]]])
        ),
      json =>
        val obj = json.obj
        val meta = read[AskReplyMeta](obj("meta").str)
        val list = read[List[Reply[T]]](obj("list").str)
        ReplyBatch(meta, list)
    )

  //////////////////////////////////////////////////////////////////////////////
  // HELPER METHODS FOR MANAGING AND FILTERING OUT REMOTE EVENTS
  //////////////////////////////////////////////////////////////////////////////

  // NOTE: We have chosen the delimiter to be `%` because it does not overlap
  // with the `$` which is used for anonymous generated names in the runtime,
  // for now.

  /** The prefix of a remote path. */
  inline val PREFIX = "REMOTE%"

  /** Create a remote path from the `url` and `path`. */
  inline def REMOTE_PATH(url: String, path: String): String = PREFIX + url + "%" + path

  /** Check if `str` has the prefix. */
  inline def HAS_PREFIX(inline str: String): Boolean = str.startsWith(PREFIX)

  /** Extract the < URL, PATH > from a Remote path. */
  inline def SPLIT(str: String): (String, String) = { val s = str.split("\\%"); (s(1), s(2)) }

  inline def GET_URL(str: String): String = SPLIT(str)._1

  inline def GET_PATH(str: String): String = SPLIT(str)._2

  /** Check if `batch` is a remote event. */
  inline def REMOTEFILTER(batch: EventBatch): Boolean = batch match
    case AskBatch(AskReplyMeta(portal, _, _, _), _) if HAS_PREFIX(portal) => true
    case ReplyBatch(AskReplyMeta(_, askingWF, _, _), _) if HAS_PREFIX(askingWF) => true
    case _ => false

  /** Remove the remote prefix and url from a path. */
  inline def UN_REMOTE_PATH(path: String): String =
    GET_PATH(path)

  /** Transform an event batch, to be run on the replier before feeding. */
  inline def TRANSFORM_REQ_TO_REP(batch: EventBatch): EventBatch = batch match
    case AskBatch(meta, list) =>
      AskBatch(
        meta.copy(
          portal = UN_REMOTE_PATH(meta.portal),
          askingWF = REMOTE_PATH(GET_URL(meta.portal), meta.askingWF),
        ),
        list.map {
          case Ask(key, meta, event) =>
            Ask(
              key,
              meta.copy(
                portal = UN_REMOTE_PATH(meta.portal),
                askingWF = REMOTE_PATH(GET_URL(meta.portal), meta.askingWF),
              ),
              event
            )
          case _ => ???
        }
      )
    case _ => ??? // should never happen

  /** Transform an event batch, to be run on the asker before feeding. */
  inline def TRANSFORM_REP_TO_REQ(batch: EventBatch): EventBatch = batch match
    case ReplyBatch(meta, list) =>
      ReplyBatch(
        meta.copy(
          portal = REMOTE_PATH(GET_URL(meta.askingWF), meta.portal),
          askingWF = UN_REMOTE_PATH(meta.askingWF),
        ),
        list.map {
          case Reply(key, meta, event) =>
            Reply(
              key,
              meta.copy(
                portal = REMOTE_PATH(GET_URL(meta.askingWF), meta.portal),
                askingWF = UN_REMOTE_PATH(meta.askingWF),
              ),
              event
            )
          case _ => ???
        }
      )
    case _ => ??? // should never happen

  inline def TRANSFORM_BATCH(batch: EventBatch): EventBatch = batch match
    case AskBatch(_, _) =>
      TRANSFORM_REQ_TO_REP(batch)
    case ReplyBatch(_, _) =>
      TRANSFORM_REP_TO_REQ(batch)
    case _ => ??? // should never happen

  inline def GET_URL(batch: EventBatch): String = batch match
    case AskBatch(meta, _) =>
      GET_URL(meta.portal)
    case ReplyBatch(meta, _) =>
      GET_URL(meta.askingWF)
    case _ => ??? // should never happen

  inline def TRANSFORM_ASK_1(batch: EventBatch, url: String): EventBatch = batch match
    case AskBatch(meta, list) =>
      AskBatch(
        meta.copy(
          askingWF = REMOTE_PATH(url, meta.askingWF),
        ),
        list.map {
          case Ask(key, meta, event) =>
            Ask(
              key,
              meta.copy(
                askingWF = REMOTE_PATH(url, meta.askingWF),
              ),
              event
            )
          case _ => ???
        }
      )
    case _ => ???

  inline def TRANSFORM_ASK_2(batch: EventBatch): EventBatch = batch match
    case AskBatch(meta, list) =>
      AskBatch(
        meta.copy(
          portal = GET_PATH(meta.portal),
        ),
        list.map {
          case Ask(key, meta, event) =>
            Ask(
              key,
              meta.copy(
                portal = GET_PATH(meta.portal),
              ),
              event
            )
          case _ => ???
        }
      )
    case _ => ???

  inline def swapApps(str1: String, str2: String): String =
    val t1 = str2.split("/").toList.tail.head
    val s1 = "/" + t1 + "/" + "portals/" + str1
    s1

  inline def TRANSFORM_REP_1(batch: EventBatch): EventBatch = batch match
    case ReplyBatch(meta, list) =>
      ReplyBatch(
        meta.copy(
          portal = swapApps(meta.portal, UN_REMOTE_PATH(meta.askingWF)),
          askingWF = UN_REMOTE_PATH(meta.askingWF),
        ),
        list.map {
          case Reply(key, meta, event) =>
            Reply(
              key,
              meta.copy(
                portal = swapApps(meta.portal, UN_REMOTE_PATH(meta.askingWF)),
                askingWF = UN_REMOTE_PATH(meta.askingWF),
              ),
              event
            )
          case _ => ???
        }
      )
    case _ => ???
