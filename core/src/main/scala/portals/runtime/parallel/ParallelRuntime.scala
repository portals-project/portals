package portals

class ParallelRuntime extends AkkaLocalRuntime:
  override val runner: AkkaRunner = AkkaRunnerImpl
