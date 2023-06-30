#!/bin/bash
sbt compile;
sbt fastLinkJS;
sbt fastOptJS;
sbt fullOptJS;
node portals-js/js/src/main/resources/obfuscator.js;
