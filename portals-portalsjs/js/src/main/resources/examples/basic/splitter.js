let builder = PortalsJS.ApplicationBuilder("splitterTest");
let generator = builder.generators.fromRange(0, 128, 8);
let splitter = builder.splitters.empty(generator.stream);
let splitEven = builder.splits.split(splitter, x => x % 2 == 0);
let splitOdd = builder.splits.split(splitter, x => x % 2 == 1);
builder.workflows
  .source(splitEven)
  .logger("splitEven: ")
  .sink()
  .freeze();
builder.workflows
  .source(splitOdd)
  .logger("splitOdd: ")
  .sink()
  .freeze();
let splitterTest = builder.build();
let system = PortalsJS.System();
system.launch(splitterTest);
system.stepUntilComplete();
system.shutdown();
