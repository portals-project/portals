#!/bin/bash
sbt compile;
sbt fastLinkJS;
sbt fastOptJS;
sbt fullOptJS;
node portals-portalsjs/js/src/main/resources/obfuscator.js;
