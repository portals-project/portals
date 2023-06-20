var builder = PortalsJS.ApplicationBuilder("app")
var generator = builder.generators.fromRange(0, 128, 8)
var splitter = builder.splitters.empty(generator.stream)
var split1 = builder.splits.split(splitter, x => x % 2 == 0)
var split2 = builder.splits.split(splitter, x => x % 2 == 1)
var _ = builder
    .workflows
    .source(split1)
    .logger("split 1: ")
    .sink()
    .freeze()
var _ = builder
    .workflows
    .source(split2)
    .logger("split 2: ")
    .sink()
    .freeze()
var app = builder.build()
var system = PortalsJS.System()
system.launch(app)
system.stepUntilComplete()
