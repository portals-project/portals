var builder = PortalsJS.ApplicationBuilder("appName")

var generator = builder.generators
  .fromArrayOfArrays([
    [0, 1, 2, 3, 4, 5, 6],
    [7, 8, 9, 10]]
  )

var workflow = builder.workflows
  .source(generator.stream)
  .map(ctx => x => x * 3)
  .processor(ctx => x => {
    ctx.emit(x * 3)
  })
  .filter(x => x % 2 == 0)
  .logger("fromWorkflow: ")
  .withOnAtomComplete(ctx => ctx.log.info("Atom"))
  .sink()
  .freeze()

var app = builder.build()

var system = PortalsJS.System()

system.launch(app)

system.stepUntilComplete()

system.registry.show
