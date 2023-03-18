package portals.util

import scala.scalajs.js.annotation.JSExport

trait Logger:
  val name: String
  def log = this
  @JSExport
  def info(msg: Any): Unit =
    val _msg = Logger._level + name + " - " + msg.toString()
    println(_msg)

object Logger:
  setLevel("INFO") // set log level to INFO
  var _level = ""

  def apply(_name: String): Logger =
    new Logger { val name = _name }

  def setLevel(level: String): Unit =
    _level = level
