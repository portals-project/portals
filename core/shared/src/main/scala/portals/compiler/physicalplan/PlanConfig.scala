package portals.compiler.physicalplan

/** Configuration for a physical plan. */
private[portals] sealed trait PlanConfig

/** Base configuration of a physical plan with a keyrange.
  *
  * @param keyrange
  *   The keyrange of this plan, right-open, i.e. [start, end), (exclusive end)
  *   (except for Long.MaxValue).
  */
private[portals] case class BaseConfig(keyrange: (Long, Long)) extends PlanConfig

/** Empty configuration of a physical plan. To be used for building a plan. */
private[portals] object EmptyConfig extends PlanConfig
