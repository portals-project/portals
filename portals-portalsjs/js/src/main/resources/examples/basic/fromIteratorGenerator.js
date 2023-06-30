let array = [1, 2, 3, 4];
let it = array[Symbol.iterator]();
var builder = PortalsJS.ApplicationBuilder("Iterator")
var _ = builder.workflows
    .source(builder.generators.fromIterator(it).stream)
    .logger()
    .sink()
    .freeze()
var iter = builder.build()
var system = PortalsJS.System()
system.launch(iter)
system.stepUntilComplete()
