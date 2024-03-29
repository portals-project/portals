var builder = PortalsJS.ApplicationBuilder("helloWorld")
var _ = builder.workflows
  .source(builder.generators.fromArray(["Hello World!"]).stream)
  .logger()
  .sink()
  .freeze()
var helloWorld = builder.build()
var system = PortalsJS.System()
system.launch(helloWorld)
system.stepUntilComplete()
