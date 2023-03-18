# PortalsJS

## Compile, Test, and Run
The final compiled file is: `portals-js/js/target/scala-X/portals-js.js`.

Install packages:
```
npm install javascript-obfuscator fs
```

Compile:
```
sbt compile;
sbt fastLinkJS;
sbt fastOptJS;
sbt fullOptJS;
node portals-js/js/src/main/resources/obfuscator.js;
```

Test:
```
cd portals-js/js/src/main/resources/;
node test-runner.js;
```

To run:
Load the JS file, see https://github.com/portals-project/playground.