let builder = PortalsJS.ApplicationBuilder("processor");
let _ = builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
  .processor(ctx => x => ctx.emit(x * 2))
  .logger()
  .sink()
  .freeze();
let processor = builder.build();
let system = PortalsJS.System();
system.launch(processor);
system.stepUntilComplete();
