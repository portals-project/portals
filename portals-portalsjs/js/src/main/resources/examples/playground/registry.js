let builder1 = PortalsJS.ApplicationBuilder("app1");
let generator1 = builder1.generators.fromRange(0, 16, 1);
let workflow1 = builder1.workflowsWithName("workflow1")
  .source(generator1.stream)
  .map(ctx => x => x * x)
  .sink()
  .freeze();
let app1 = builder1.build();

let builder2 = PortalsJS.ApplicationBuilder("app2");
let app1Stream = builder2.registry.streams.get("/app1/workflows/workflow1/stream");
let workflow2 = builder2.workflows
  .source(app1Stream)
  .map(ctx => x => x * x)
  .logger()
  .sink()
  .freeze();
let app2 = builder2.build();

let system = PortalsJS.System();
system.launch(app1);
system.launch(app2);
system.stepUntilComplete();
