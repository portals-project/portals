package portals.compiler.physicalplan

import portals.application.Application

/** Physical plan representation of an application.
  *
  * @tparam CONFIG
  *   The configuration-type of the physical plan.
  */
private[portals] sealed trait PhysicalPlan[CONFIG <: PlanConfig] {

  /** Returns the configuration of this physical plan.
    *
    * @return
    *   the configuration of this physical plan.
    */
  def config: CONFIG
}

/** Plan representation of an application.
  *
  * @tparam CONFIG
  *   The configuration-type of the physical plan.
  * @param app
  *   The application that this plan represents.
  * @param config
  *   The configuration of this plan.
  */
private[portals] case class Plan[CONFIG <: PlanConfig](app: Application, config: CONFIG) extends PhysicalPlan[CONFIG]
