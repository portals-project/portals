package portals.distributed.remote

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import portals.api.builder.*
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.application.task.*
import portals.distributed.server.SBTRunServer
import portals.distributed.server.Server
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

  // implicit def OptionReader[T: Reader]: Reader[Option[T]] = reader[ujson.Value].map[Option[T]] {
  //   case ujson.Null => None
  //   case jsValue => Some(read[T](jsValue))
  // }
  // implicit def OptionWriter[T: Writer]: Writer[Option[T]] = writer[ujson.Value].comap {
  //   case Some(value) => write(value)
  //   case None => ujson.Null
  // }

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
  /** The prefix of a remote path. */
  inline val PREFIX = "REMOTE$"

  /** Create a remote path from the `url` and `path`. */
  inline def REMOTE_PATH(url: String, path: String): String = PREFIX + url + "$" + path

  /** Check if `str` has the prefix. */
  inline def HAS_PREFIX(inline str: String): Boolean = str.startsWith(PREFIX)

  /** Extract the < URL, PATH > from a Remote path. */
  inline def SPLIT(str: String): (String, String) = { val s = str.split("\\$"); println(s); (s(1), s(2)) }

  inline def GET_URL(str: String): String = SPLIT(str)._1

  inline def GET_PATH(str: String): String = SPLIT(str)._2

  /** Check if `batch` is a remote event. */
  inline def REMOTEFILTER(batch: EventBatch): Boolean = batch match
    case AskBatch(AskReplyMeta(portal, _, _, _), _) if HAS_PREFIX(portal) => true
    case ReplyBatch(AskReplyMeta(_, askingWF, _, _), _) if HAS_PREFIX(askingWF) => true
    case _ => false

  // /** Replace with the provided `url` for a remote meta. */
  // inline def REPLACE_URL(meta: AskReplyMeta, url: String): AskReplyMeta =
  //   val (oldUrl, oldPath) = SPLIT(meta.portal)
  //   meta.copy(portal = REMOTE_PATH(url, oldPath))

  // /** Replace with the provided `url` for a batch of remote events. */
  // inline def REPLACE_URL(batch: EventBatch, url: String): EventBatch = batch match
  //   case AskBatch(meta, list) =>
  //     AskBatch(REPLACE_URL(meta, url), list)
  //   case ReplyBatch(meta, list) =>
  //     ReplyBatch(REPLACE_URL(meta, url), list)
  //   case _ => ??? // should never happen

  /** Remove the remote prefix and url from a path. */
  inline def UN_REMOTE_PATH(path: String): String =
    GET_PATH(path)

  // /** Remove the remote prefix and url from a batch. */
  // inline def UN_REMOTE_BATCH(batch: EventBatch): EventBatch = batch match
  //   case AskBatch(meta, list) =>
  //     AskBatch(meta.copy(portal = UN_REMOTE_PATH(meta.portal)), list)
  //   case ReplyBatch(meta, list) =>
  //     ReplyBatch(meta.copy(portal = UN_REMOTE_PATH(meta.portal)), list)
  //   case _ => ??? // should never happen

  /** Transform an event batch, to be run on the replier before feeding. */
  inline def TRANSFORM_REQ_TO_REP(batch: EventBatch): EventBatch = batch match
    case AskBatch(meta, list) =>
      AskBatch(
        meta.copy(
          portal = UN_REMOTE_PATH(meta.portal),
          askingWF = REMOTE_PATH(GET_URL(meta.portal), meta.askingWF),
        ),
        list
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
        list
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

object Test extends App:
  import RemoteShared.given

  def serDeser[T: Reader: Writer](arm: T): T =
    println(arm)
    val x = write[T](arm)
    println(x)
    val y = read[T](x)
    println(y)
    y

  val arm = AskReplyMeta("a", "b", Some("c"), None)
  val arm2 = serDeser(arm)
  // val arm2 = AskReplyMeta("a", "b", None, None)
  val arm3 = serDeser(arm2)

  val ask1 = Ask[Int](
    Key(0),
    PortalMeta(
      "a",
      "b",
      Key(0),
      1,
      "askingwf"
    ),
    12
  )

  val askBatch = AskBatch(arm, List(ask1, ask1))

  serDeser(askBatch)
