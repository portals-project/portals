let builder = PortalsJS.ApplicationBuilder("multiDataflow")

let sequencer = builder.sequencers.random()

let workflow1 = builder.workflows
  .source(sequencer.stream)
  .flatMap(ctx => x => { if (x <= 0) { return []; } else { return [x - 1]; } })
  .logger("workflow1: ")
  .sink()
  .freeze()

let generator = builder.generators.signal(8)
builder.connections.connect(generator.stream, sequencer)
builder.connections.connect(workflow1.stream, sequencer)

let workflow2 = builder.workflows
  .source(workflow1.stream)
  .map(ctx => x => { return x * x; })
  .logger("workflow2: ")
  .sink()
  .freeze()

let workflow3 = builder.workflows
  .source(workflow1.stream)
  .map(ctx => x => { return x * x * x; })
  .logger("workflow3: ")
  .sink()
  .freeze()

let multiDataflow = builder.build()
let system = PortalsJS.System()
system.launch(multiDataflow)
system.stepUntilComplete()
