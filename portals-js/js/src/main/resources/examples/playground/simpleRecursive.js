var builder = PortalsJS.ApplicationBuilder("simpleRecursive")
var gen = builder.generators.fromArray([10])
var seq = builder.sequencers.random()
var recursiveWorkflow = builder.workflows
  .source(seq.stream)
  .processor(ctx => x => {
    if (x > 0) {
      ctx.emit(x - 1)
    }
  })
  .logger()
  .sink()
  .freeze()
var _ = builder.connections.connect(gen.stream, seq)
var _ = builder.connections.connect(recursiveWorkflow.stream, seq)
var simpleRecursive = builder.build()
var system = PortalsJS.System()
system.launch(simpleRecursive)
system.stepUntilComplete()