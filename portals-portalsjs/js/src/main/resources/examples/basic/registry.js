let builder1 = PortalsJS.ApplicationBuilder("registryTest1");
let generator = builder1.generatorsWithName("name").fromRange(0, 128, 8);
let app1 = builder1.build();
let builder2 = PortalsJS.ApplicationBuilder("registryTest2");
let foreignStream = builder2.registry.streams.get("/registryTest1/generators/name/stream");
builder2.workflows
  .source(foreignStream)
  .logger("from app2: ")
  .sink()
  .freeze();
let app2 = builder2.build();
let system = PortalsJS.System();
system.launch(app1);
system.launch(app2);
system.stepUntilComplete();
system.shutdown();
