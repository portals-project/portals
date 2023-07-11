// hashcode function to compute key of a string
let hashCode = str => {
  var hash = 0;
  for (var i = 0; i < str.length; i++) {
    let c = str.charCodeAt(i);
    hash = ((hash << 5) - hash) + c;
    hash = hash & hash;
    return hash;
  }
}

let builder = PortalsJS.ApplicationBuilder("application");

let input = ["the quick brown fox jumps over the lazy dog"];
let generator = builder.generators.fromArray(input);

let _ = builder.workflows
  .source(generator.stream)
  .flatMap(ctx => line => line.split(/\s+/))
  .map(ctx => word => [word, 1])
  .key(pair => { return hashCode(pair[0]); })
  .processor(ctx => x => {
    let count = PortalsJS.PerKeyState("count", 0, ctx);
    count.set(count.get() + x[1]);
    ctx.emit([x[0], count.get()]);
  })
  .logger()
  .sink()
  .freeze();

let application = builder.build();
let system = PortalsJS.System();
system.launch(application);
system.stepUntilComplete();
