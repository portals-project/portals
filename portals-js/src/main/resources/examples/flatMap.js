var builder = PortalsJS.ApplicationBuilder("flatMap");
var _ = builder.workflows
    .source(builder.generators.fromRange(0, 128, 8).stream)
    .flatMap(ctx => x => {
        var squares = [x * x, x * x * x]; // Generate an array of squares and cubes
        return squares;
    })
    .logger()
    .sink()
    .freeze();
var fmap = builder.build();
var system = PortalsJS.System();
system.launch(fmap);
system.stepUntilComplete();