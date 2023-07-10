let builder = PortalsJS.ApplicationBuilder("task");
let t = builder.tasks.map(ctx => x => x * x);
let _ = builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
  .task(t)
  .logger()
  .sink()
  .freeze();
let task = builder.build();
let system = PortalsJS.System();
system.launch(task);
system.stepUntilComplete();
