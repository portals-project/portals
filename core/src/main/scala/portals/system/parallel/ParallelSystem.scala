package portals.system.parallel

class ParallelSystem extends AkkaLocalSystem:
  override val runner: AkkaRunner = AkkaRunnerImpl
