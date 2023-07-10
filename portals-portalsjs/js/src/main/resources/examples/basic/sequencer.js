let builder = PortalsJS.ApplicationBuilder("sequencerTest");
let sequencer = builder.sequencers.random();
let generator1 = builder.generators.fromRange(0, 128, 8);
let generator2 = builder.generators.fromRange(0, 128, 8);
builder.connections.connect(generator1.stream, sequencer);
builder.connections.connect(generator2.stream, sequencer);
builder.workflows
  .source(sequencer.stream)
  .identity()
  .logger()
  .sink()
  .freeze();
let sequencerTest = builder.build();
let system = PortalsJS.System();
system.launch(sequencerTest);
system.stepUntilComplete();
system.shutdown();
