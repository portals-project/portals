#!/bin/bash
sbt compile;
sbt fastLinkJS;
sbt fastOptJS;
sbt fullOptJS;
