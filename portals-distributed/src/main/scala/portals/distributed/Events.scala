package portals.distributed

import upickle.default._

/** `Events` handled by the `Server`. */
object Events:
  /** `Launch` the `application` specified by its Java Path.
    *
    * Note: the corresponding classfiles will need to have been uploaded in
    * advance.
    */
  case class Launch(application: String) derives ReadWriter

  /** The `path` and `bytes` of a classfile to be uploaded. */
  case class ClassFileInfo(path: String, bytes: Array[Byte]) derives ReadWriter

  /** Submit the `classFiles` to the server. */
  case class SubmitClassFiles(classFiles: Seq[ClassFileInfo]) derives ReadWriter
