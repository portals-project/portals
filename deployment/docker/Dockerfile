FROM sbtscala/scala-sbt:openjdk-oraclelinux8-11.0.16_1.8.1_3.2.1
WORKDIR /app

COPY ./portals-distributed/ /app/portals-distributed
COPY ./portals-core/ /app/portals-core

# Necessary because of imports in portals-distributed/examples
COPY ./portals-examples/ /app/portals-examples 

COPY ./project/ /app/project
COPY ./build.sbt /app/build.sbt
RUN sbt compile 
EXPOSE 8080
ENTRYPOINT sbt "~distributed/runMain portals.distributed.Server"

