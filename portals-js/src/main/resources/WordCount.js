var builder = PortalsJS.ApplicationBuilder("wordCount")
var input = Array("the quick brown fox jumps over the lazy dog")
var generator = builder.generators.fromArray(input)
var _ = builder
    .workflows
    .source(generator.stream, String)
    .flatMap(line => line.split(/\s+/))
    .map(w => [w, 1])
    .key(w => w[0].hashCode())
    .init(ctx => {
            const counts = PortalsJS.PerTaskState("counts", new Map(), ctx);
            return PortalsJS.TaskBuilder.processor( (k, v) => {
                const newCount = (counts.get().get(k) || 0) + v;
                counts.set(counts.get().set(k, newCount));
            });
    })
    .withOnAtomComplete(ctx => {
        // emit final state
        const counts = PortalsJS.PerTaskState("counts", new Map(), ctx);
        counts.get().forEach((v, k) => ctx.emit(k, v));
        ctx.state.clear();
    })
    .logger()
    .checkExpectedType([String, Number])
    .sink()
    .freeze();
var map = builder.build()
var system = PortalsJS.System()
system.launch(map)
system.stepUntilComplete()

