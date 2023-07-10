let builder = PortalsJS.ApplicationBuilder("withStar");
let _ = builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
  .identity()
  .withName("withStar")
  .withOnNext(ctx => x => ctx.emit(-x))
  .withOnAtomComplete(ctx => ctx.emit(1))
  .withOnComplete(ctx => ctx.emit(2))
  .logger()
  .sink()
  .freeze();
let withStar = builder.build();
let system = PortalsJS.System();
system.launch(withStar);
system.stepUntilComplete();
system.shutdown();
