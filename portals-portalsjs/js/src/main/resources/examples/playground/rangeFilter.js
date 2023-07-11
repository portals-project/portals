var builder = PortalsJS.ApplicationBuilder("rangeFilter")
var _ = builder.workflows
  .source(builder.generators.fromRange(0, 1024, 8).stream)
  .filter(x => x % 2 == 0)
  .logger()
  .sink()
  .freeze()
var rangeFilter = builder.build()
var system = PortalsJS.System()
system.launch(rangeFilter)
system.stepUntilComplete()