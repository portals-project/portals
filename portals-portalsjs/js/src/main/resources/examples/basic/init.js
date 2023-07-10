let builder = PortalsJS.ApplicationBuilder("init");
let _ = builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
  .key(x => 0)
  .init(ctx => {
    let state = PortalsJS.PerKeyState("state", 0, ctx);
    return builder.tasks.map(c => x => {
      state.set(state.get() + x);
      return state.get();
    });
  })
  .logger()
  .sink()
  .freeze();
let init = builder.build();
let system = PortalsJS.System();
system.launch(init);
system.stepUntilComplete();
