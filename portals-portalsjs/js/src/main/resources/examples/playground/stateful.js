let builder = PortalsJS.ApplicationBuilder("stateful")

let generator = builder.generators.fromRange(0, 128, 1)

let workflow = builder.workflows
  .source(generator.stream)
  .key(x => (x % 4))
  .processor(ctx => x => {
    let state = PortalsJS.PerKeyState("state", 0, ctx);
    state.set(state.get() + x);
    ctx.emit(state.get());
    return;
  })
  .logger()
  .sink()
  .freeze()

let stateful = builder.build()
let system = PortalsJS.System()
system.launch(stateful)
system.stepUntilComplete()
