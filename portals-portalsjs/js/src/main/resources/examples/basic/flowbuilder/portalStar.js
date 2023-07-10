let builder = PortalsJS.ApplicationBuilder("portalStar");
let portal = builder.portal.portal("portal");
builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
  .replier(portal, ctx => x => { }, ctx => x => ctx.reply(-x))
  .logger()
  .sink()
  .freeze();
builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
  .asker(portal, ctx => x => {
    let f = ctx.ask(portal, x);
    ctx.await(f, c => {
      ctx.emit(f.value(ctx));
    });
  })
  .logger()
  .sink()
  .freeze();
let portalStar = builder.build();
let system = PortalsJS.System();
system.launch(portalStar);
system.stepUntilComplete();
system.shutdown();
