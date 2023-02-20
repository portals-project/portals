package portals.compiler.physicalplan

sealed trait PlanConfig
case class BaseConfig(keyrange: (Long, Long)) extends PlanConfig
object EmptyConfig extends PlanConfig
