FROM sbtscala/scala-sbt:openjdk-oraclelinux8-11.0.16_1.8.1_3.2.1

COPY ./portals-benchmark/ /app/portals-benchmark
COPY ./portals-core/ /app/portals-core
COPY ./portals-distributed/ /app/portals-distributed
COPY ./portals-examples/ /app/portals-examples 
COPY ./portals-libraries/ /app/portals-libraries 
COPY ./project/ /app/project
COPY ./build.sbt /app/build.sbt

WORKDIR /app

RUN sbt compile

EXPOSE 8080

CMD sbt "distributed/runMain portals.distributed.SBTRunServer 0.0.0.0 8080"
