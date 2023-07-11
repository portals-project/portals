let builder = PortalsJS.ApplicationBuilder("flatMap");
let _ = builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
  .flatMap(ctx => x => { return [x, x * x]; })
  .logger()
  .sink()
  .freeze();
let flatMap = builder.build();
let system = PortalsJS.System();
system.launch(flatMap);
system.stepUntilComplete();
system.shutdown();
