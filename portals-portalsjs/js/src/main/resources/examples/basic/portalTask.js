let builder = PortalsJS.ApplicationBuilder("portalTask");
let portal = builder.portal.portal("portal");
builder.workflows
  .source(builder.generators.fromRange(0, 128, 8).stream)
  .askerreplier(portal, portal, ctx => x => {
    let f = ctx.ask(portal, x);
    ctx.await(f, c => {
      ctx.emit(f.value(ctx));
    });
  }, ctx => x => ctx.reply(-x))
  .logger()
  .sink()
  .freeze();
let portalTask = builder.build();
let system = PortalsJS.System();
system.launch(portalTask);
system.stepUntilComplete();
