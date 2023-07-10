let builder = PortalsJS.ApplicationBuilder("taskStateTest");
let generator = builder.generators.fromRange(0, 128, 8);
builder.workflows
  .source(generator.stream)
  .key(x => x % 16)
  .processor(ctx => x => {
    let state = PortalsJS.PerKeyState("state", 0, ctx);
    state.set(state.get() + 1);
    ctx.emit(state.get());
  })
  .logger()
  .sink()
  .freeze();
let taskStateTest = builder.build();
let system = PortalsJS.System();
system.launch(taskStateTest);
system.stepUntilComplete();
system.shutdown();
