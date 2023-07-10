let builder = PortalsJS.ApplicationBuilder("taskTest");
let task1 = builder.tasks.map(ctx => x => x + 1);
let task2 = builder.tasks.processor(ctx => x => ctx.emit(x - 1));
let task3 = builder.tasks.filter(x => x % 2 == 0);
let task4 = builder.tasks.flatMap(ctx => x => [x, -x]);
let generator = builder.generators.fromRange(0, 128, 8);
builder.workflows
  .source(generator.stream)
  .task(task1)
  .task(task2)
  .task(task3)
  .task(task4)
  .logger()
  .sink()
  .freeze();
let taskTest = builder.build();
let system = PortalsJS.System();
system.launch(taskTest);
system.stepUntilComplete();
system.shutdown();
