package portals.util

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

trait Logger:
  val name: String
  def log = this
  @JSExport
  def info(msg: Any): Unit =
    val _msg = Logger._level + name + " - " + msg.toString()
    println(Logger.getTimestamp + " INFO " + _msg)

object Logger:
  setLevel("INFO") // set log level to INFO
  var _level = ""

  def apply(_name: String): Logger =
    new Logger { val name = _name }

  def setLevel(level: String): Unit =
    _level = level

  private def formatNumber(number: Int, digits: Int): String =
    val padded = "0" * (digits - number.toString.length) + number.toString
    padded.substring(0, digits)

  private def getTimestamp: String =
    val date = new js.Date()
    val hours = formatNumber(date.getHours().toInt, 2)
    val minutes = formatNumber(date.getMinutes().toInt, 2)
    val seconds = formatNumber(date.getSeconds().toInt, 2)
    val milliseconds = formatNumber(date.getMilliseconds().toInt, 3)
    s"$hours:$minutes:$seconds.$milliseconds"
