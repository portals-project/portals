# Portals

[![Build Status](https://github.com/portals-project/portals/actions/workflows/build-test.yaml/badge.svg)](https://github.com/portals-project/portals/actions/workflows/build-test.yaml)

Welcome to the Portals repo!

## Getting Started Guide
We recommend the following steps to get started.
* [Install Scala](https://www.scala-lang.org/download/), we recommend working with sbt, together with [Metals](https://scalameta.org/metals/docs/editors/vscode/) on VS Code. 
* Clone the repository `git clone https://github.com/portals-project/portals.git`.
* Compile the project `sbt compile`.
* Check out the [examples](examples/src/main/scala/portals/examples) or [tests](core/src/test/scala/portals).

## Examples
Examples can be found in the [examples](examples/src/main/scala/portals/examples) directory. You can run an example by running the command `sbt examples/run`.

## Contributing
Contributions are welcome! To contribute please follow these guideines.
* Familiarize yourself with the technologies and concepts.
  * Scala 3: The [Scala 3 docs](https://docs.scala-lang.org/) is a great resource. 
  * Actors: [Akka Typed Actors](https://doc.akka.io/docs/akka/2.5.32/typed/index.html).
  * Dataflow: [Flink](https://github.com/ververica/flink-training) and more [Flink](https://flink.apache.org/).
  * Stateful serverless: [Durable Functions](https://docs.microsoft.com/en-us/azure/azure-functions/durable/) and [Flink StateFun](https://nightlies.apache.org/flink/flink-statefun-docs-master/).
  * Other projects: [Reactors](http://reactors.io/), [Kalix (Akka Serverless)](https://docs.kalix.io/java/).
* To contribute we follow the [Akka guidelines](https://github.com/akka/akka/blob/main/CONTRIBUTING.md):
  1. Choose an issue (or create an issue).
  2. Fork the repo.
  3. Implement and test your implementation, add documentation.
  4. Format the code by running `sbt scalafmt` (and `sbt scalafmtAll` to format all files, incl. test and sbt `sbt scalafmtSbt`) (`sbt scalafmtAll; sbt scalafmtSbt;`).
  5. Check that the tests pass `sbt test`, and that the formatting is correct `sbt scalafmtCheckAll` and `sbt scalafmtSbtCheck` (`sbt test; sbt scalafmtCheckAll; sbt scalafmtSbtCheck;`).
  6. [Group/squash commit](https://github.com/akka/akka/blob/main/CONTRIBUTING.md#creating-commits-and-writing-commit-messages).
  7. Submit a pull-request (include a reference to the issue).
  8. After review and approval (LGTM) the pull-request will be merged. 
