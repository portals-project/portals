let builder = PortalsJS.ApplicationBuilder("filter");
let _ = builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
  .filter(x => x % 2 == 0)
  .logger()
  .sink()
  .freeze();
let filter = builder.build();
let system = PortalsJS.System();
system.launch(filter);
system.stepUntilComplete();
system.shutdown();
