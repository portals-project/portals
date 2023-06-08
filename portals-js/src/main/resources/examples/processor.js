var builder = PortalsJS.ApplicationBuilder("flatMap");
var _ = builder.workflows
    .source(builder.generators.fromRange(0, 128, 8).stream)
    .processor(ctx => x =>
        ctx.emit(x*2)
    )
    .logger()
    .sink()
    .freeze();
var processor = builder.build();
var system = PortalsJS.System();
system.launch(processor);
system.stepUntilComplete();