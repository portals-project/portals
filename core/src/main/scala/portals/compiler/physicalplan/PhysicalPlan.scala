package portals.compiler.physicalplan

import portals.*

sealed trait PhysicalPlan[CONFIG <: PlanConfig] { def config: CONFIG }
case class Plan[CONFIG <: PlanConfig](app: Application, config: CONFIG) extends PhysicalPlan[CONFIG]
