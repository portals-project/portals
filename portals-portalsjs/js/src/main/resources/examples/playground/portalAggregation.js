let builder = PortalsJS.ApplicationBuilder("portalAggregation")

let portal = builder.portal.portal("portal", x => { return 0; })

let generator = builder.generators.fromRange(0, 128, 8)

let aggregationWorkflow = builder.workflows
  .source(generator.stream)
  .key(x => { return 0; })
  .replier(
    portal,
    ctx => x => {
      // aggregate state from generator input
      let state = PortalsJS.PerKeyState("state", 0, ctx);
      state.set(x + state.get());
      ctx.emit(state.get());
    },
    ctx => x => {
      // reply to portal requests with the aggregated state
      let state = PortalsJS.PerKeyState("state", 0, ctx);
      ctx.reply(state.get());
    },
  )
  .sink()
  .freeze()

let trigger = builder.generators.fromArrayOfArrays([[0], [0], [0], [0], [0]])

let requestingWorkflow = builder.workflows
  .source(trigger.stream)
  .asker(
    portal,
    ctx => x => {
      // query the portal for the aggregated state
      let future = ctx.ask(portal, x);
      ctx.await(future, ctx => { ctx.emit(future.value(ctx)); });
    },
  )
  .logger("requestingWorkflow: ")
  .sink()
  .freeze()

let portalAggregation = builder.build()
let system = PortalsJS.System()
system.launch(portalAggregation)
system.stepUntilComplete()
