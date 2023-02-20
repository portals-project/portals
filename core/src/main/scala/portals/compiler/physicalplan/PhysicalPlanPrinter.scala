package portals.compiler.physicalplan

import portals.*

object PhysicalPlanPrinter:
  def println(ast: PhysicalPlan[_]): Unit = pprint.pprintln(ast)
  def toString(ast: PhysicalPlan[_]): String = pprint.apply(ast).plainText
