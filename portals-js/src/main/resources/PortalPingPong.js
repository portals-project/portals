const app = new PortalsJS.PortalsApp("PortalPingPong", () => {
    class PingPong {}
    class Ping extends PingPong {
        constructor(x) {
            super();
            this.x = x;
        }
    }
    class Pong extends PingPong {
        constructor(x) {
            super();
            this.x = x;
        }
    }

    const generator = Generators.fromList(Array(1024 * 128).fill(0));

    const portal = new Portal("portal", Ping, Pong);

    const replier = new Workflows("replier", null, null)
        .source(Generators.empty.stream)
        .replier(portal, () => {})((_, message) => {
            if (message instanceof Ping) {
                return new Pong(message.x - 1);
            }
        })
        .sink()
        .freeze();

    const asker = new Workflows("asker", Number, Number)
        .source(generator.stream)
        .recursiveAsker(portal)((self, x) => {
            const future = ask(portal)(new Ping(x));
            future.await(() => {
                const value = future.value.get;
                ctx.emit(value.x);
                if (value.x > 0) {
                    self(value.x);
                }
            });
        })
        .filter(value => value % 1024 === 0)
        .logger()
        .sink()
        .freeze();
});

const system = Systems.interpreter();

system.launch(app);

system.stepUntilComplete();

system.shutdown();
