let builder = PortalsJS.ApplicationBuilder("key");
let _ = builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
  .key(x => x % 2)
  .map(ctx => x => {
    let state = PortalsJS.PerKeyState("state", 0, ctx);
    state.set(state.get() + 1);
    return state.get();
  })
  .logger()
  .sink()
  .freeze();
let key = builder.build();
let system = PortalsJS.System();
system.launch(key);
system.stepUntilComplete();
system.shutdown();
