let builder = PortalsJS.ApplicationBuilder("map");
let _ = builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
  .map(ctx => x => x * x)
  .logger()
  .sink()
  .freeze();
let map = builder.build();
let system = PortalsJS.System();
system.launch(map);
system.stepUntilComplete();
system.shutdown();
