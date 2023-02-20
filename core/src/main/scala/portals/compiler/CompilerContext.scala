package portals.compiler

/** Compiler context, to be used within the compiler. */
private[portals] class CompilerContext:
  /** Log a message. */
  def log(msg: String): Unit = println(msg)
