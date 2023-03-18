# PortalsJS

## Compile
The compiled files are in `portals-js/target/scala-X/portals-js-fastopt/main.js`; `portals-js/target/scala-X/portals-js-opt/main.js`.

```
sbt compile
sbt fastLinkJS
sbt fastOptJS
sbt fullOptJS
```

## Obfuscation
Use either https://obfuscator.io/ or directly with https://github.com/javascript-obfuscator/javascript-obfuscator

## Test and Run
Open the `src/main/resources/index.html` file in a browser, and open the console editor.
