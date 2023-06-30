package portals.compiler.physicalplan

private[portals] object PhysicalPlanPrinter:
  /** Print the physical plan to the console. */
  def println(ast: PhysicalPlan[_]): Unit = pprint.pprintln(ast)

  /** Print the physical plan to a string. */
  def toString(ast: PhysicalPlan[_]): String = pprint.apply(ast).plainText
