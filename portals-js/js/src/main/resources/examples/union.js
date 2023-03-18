var builder = PortalsJS.ApplicationBuilder("map")

var flow = builder.workflows
    .source(builder.generators.fromRange(0, 128, 8).stream)

var flow1 = flow.identity()
var flow2 = flow.identity()
var _ = flow1.union(flow2).logger()
    .sink()
    .freeze()

var union = builder.build()
var system = PortalsJS.System()
system.launch(union)
system.stepUntilComplete()
