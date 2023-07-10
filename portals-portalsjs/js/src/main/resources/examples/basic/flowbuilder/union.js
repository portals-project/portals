let builder = PortalsJS.ApplicationBuilder("union");
let flow = builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
let flow1 = flow
  .identity()
let flow2 = flow
  .identity()
let _ = flow1
  .union([flow2])
  .logger()
  .sink()
  .freeze();
let union = builder.build();
let system = PortalsJS.System();
system.launch(union);
system.stepUntilComplete();
system.shutdown();
