let builder = PortalsJS.ApplicationBuilder("portalService")

let portal = builder.portal.portal("portal", x => { return 0; })

let generator = builder.generators.empty()

let replierWorkflow = builder.workflows
  .source(generator.stream)
  .replier(
    portal,
    ctx => x => { },
    ctx => x => {
      let state = PortalsJS.PerKeyState("prev", 0, ctx);
      let prev = state.get();
      ctx.reply(prev);
      state.set(x);
    },
  )
  .sink()
  .freeze()

let trigger = builder.generators.fromRange(0, 128, 8)

let requesterWorkflow = builder.workflows
  .source(trigger.stream)
  .asker(
    portal,
    ctx => x => {
      let future = ctx.ask(portal, x);
      ctx.await(future, ctx => {
        let msg = JSON.stringify({ "x": x, "future": future.value(ctx) });
        ctx.emit(msg)
      });
    },
  )
  .logger("requesterWorkflow: ")
  .sink()
  .freeze()

let portalService = builder.build()
let system = PortalsJS.System()
system.launch(portalService)
system.stepUntilComplete()
