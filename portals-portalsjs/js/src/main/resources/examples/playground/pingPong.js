let builder = PortalsJS.ApplicationBuilder("pingPong")

let portal = builder.portal.portal("pingPongPortal")

let generator = builder.generators.signal(8)

function pingerPonger(ctx, x) {
  if (x > 0) {
    ctx.log.info(x);
    let future = ctx.ask(portal, x - 1);
    ctx.await(future, ctx => {
      let v = future.value(ctx);
      ctx.reply(pingerPonger(ctx, v));
    });
  }
  else {
    ctx.reply(x);
  }
}

let pingPongWorkflow = builder.workflows
  .source(generator.stream)
  .askerreplier(
    portal,
    portal,
    ctx => x => {
      let future = ctx.ask(portal, x);
      ctx.await(future, ctx => {
        ctx.log.info("Done!");
      });
    },
    ctx => x => {
      pingerPonger(ctx, x)
    },
  )
  .logger()
  .sink()
  .freeze()

let pingPong = builder.build()
let system = PortalsJS.System()
system.launch(pingPong)
system.stepUntilComplete()
