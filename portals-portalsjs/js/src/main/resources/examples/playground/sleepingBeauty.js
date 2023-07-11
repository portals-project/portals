function sleep(milliseconds) {
  const start = Date.now();
  while (Date.now() - start < milliseconds) {}
}
let builder = PortalsJS.ApplicationBuilder("sleepingBeauty")
let _ = builder.workflows
  .source(builder.generators.fromRange(0, 1024, 2).stream)
  .map(ctx => x => {
       sleep(500);
       return x;
  })
  .logger()
  .sink()
  .freeze()
let sleepingBeauty = builder.build()
let system = PortalsJS.System()
system.launch(sleepingBeauty)
system.stepUntilComplete()