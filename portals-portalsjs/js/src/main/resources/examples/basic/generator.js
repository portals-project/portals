let builder = PortalsJS.ApplicationBuilder("generatorTest");
let generator1 = builder.generators.fromRange(0, 128, 8);
let generator2 = builder.generators.fromArray([1, 2, 3, 4, 5]);
let generator3 = builder.generators.fromArrayOfArrays([[1, 2, 3], [4, 5, 6]]);
let generator4 = builder.generators.signal("asdf");
let generator5 = builder.generators.empty();
let sequencer = builder.sequencers.random();
builder.connections.connect(generator1.stream, sequencer);
builder.connections.connect(generator2.stream, sequencer);
builder.connections.connect(generator3.stream, sequencer);
builder.connections.connect(generator4.stream, sequencer);
builder.connections.connect(generator5.stream, sequencer);
builder.workflows
  .source(sequencer.stream)
  .logger()
  .sink()
  .freeze();
let generatorTest = builder.build();
let system = PortalsJS.System();
system.launch(generatorTest);
system.stepUntilComplete();
system.shutdown();
