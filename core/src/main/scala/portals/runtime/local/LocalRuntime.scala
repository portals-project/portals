package portals.runtime.local

class LocalRuntime extends AkkaLocalRuntime:
  override val runner: AkkaRunnerBehaviors = AkkaRunnerImpl
