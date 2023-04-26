package portals.system

trait Systems

import portals.system.InterpreterSystem
import portals.system.PortalsSystem

object Systems extends Systems:
  def default(): PortalsSystem = interpreter()

  def interpreter(): InterpreterSystem = new InterpreterSystem()

  def interpreter(seed: Int): InterpreterSystem = new InterpreterSystem(Some(seed))

  def local(): PortalsSystem = new LocalSystem()
