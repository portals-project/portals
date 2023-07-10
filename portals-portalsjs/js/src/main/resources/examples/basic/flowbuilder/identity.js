let builder = PortalsJS.ApplicationBuilder("identity");
let _ = builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
  .key(x => 0)
  .identity()
  .logger()
  .sink()
  .freeze();
let identity = builder.build();
let system = PortalsJS.System();
system.launch(identity);
system.stepUntilComplete();
system.shutdown();
