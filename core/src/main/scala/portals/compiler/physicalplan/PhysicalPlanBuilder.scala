package portals.compiler.physicalplan

import portals.application.Application

private[portals] object PhysicalPlanBuilder:
  def fromApplication(app: Application): Plan[PlanConfig] =
    Plan(app, EmptyConfig)

  def withConfig(plan: Plan[_], config: PlanConfig): Plan[config.type] =
    Plan(plan.app, config)

  /** Split a physical plan into num ranges */
  def split(plan: PhysicalPlan[_], num_ranges: Int): List[PhysicalPlan[BaseConfig]] = plan match
    case Plan(app, _) =>
      val splits = compute_splits(num_ranges)
      splits.sliding(2).map(x => Plan(app, BaseConfig((x(0), x(1))))).toList

  /** Returns a sorted list of #num_ranges ranges */
  private def compute_splits(num_ranges: Int): List[Long] =
    assert(num_ranges > 0)
    if num_ranges == 1 then List(Long.MinValue, Long.MaxValue)
    else if num_ranges == 2 then List(Long.MinValue, 0L, Long.MaxValue)
    else if num_ranges % 2 == 0 then
      val splitSize = (Long.MaxValue / num_ranges.toLong) - (Long.MinValue / num_ranges.toLong)
      var splits = List(Long.MinValue, 0L, Long.MaxValue)
      for i <- 1 to ((num_ranges / 2) - 1) do
        splits = splits :+ (splitSize * i)
        splits = splits :+ (-splitSize * i)
      splits.sorted
    else
      val splitSize = (Long.MaxValue / num_ranges.toLong) - (Long.MinValue / num_ranges.toLong)
      var splits = List(Long.MinValue, -(splitSize / 2), (splitSize / 2), Long.MaxValue)
      for i <- 1 to ((num_ranges / 2) - 1) do
        splits = splits :+ ((splitSize / 2) + (splitSize * i))
        splits = splits :+ -((splitSize / 2) + (splitSize * i))
      splits.sorted
