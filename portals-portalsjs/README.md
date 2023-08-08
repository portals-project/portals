# PortalsJS

## Compile, Test, and Run
The final compiled file is: `portals-portalsjs/js/target/scala-X/portals-portalsjs-opt.js`.

Install packages:
```
npm install javascript-obfuscator fs
```

Compile:
```bash
sh scripts/portalsjs/portalsjs-compile.sh
```

Test:
```bash
sh scripts/portalsjs/portalsjs-test-runner.sh
```

Generate new test outputs:
```bash
sh scripts/portalsjs/portalsjs-test-generate-outputs.sh
```

To obfuscate, run:
```bash
# install packages
npm install javascript-obfuscator fs
# run obfuscator script
node portals-portalsjs/js/src/main/resources/obfuscator.js;
```

To run:
Load the JS file, see https://github.com/portals-project/playground.
