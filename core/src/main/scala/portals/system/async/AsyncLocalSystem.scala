package portals.system.async

class AsyncLocalSystem extends AkkaLocalSystem:
  override val runner: AkkaRunner = AkkaRunnerImpl
