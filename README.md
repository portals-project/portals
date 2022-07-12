# Portals Workflows

[![Build Status](https://github.com/jonasspenger/pods-workflows/actions/workflows/test.yaml/badge.svg)](https://github.com/jonasspenger/pods-workflows/actions/workflows/test.yaml)

Welcome to the Portals Workflows repo!

This is a fresh start, to find the old repo look through the branches for [!old-dump](https://github.com/jonasspenger/pods-workflows/tree/!old-dump).

## Getting Started Guide
We recommend the following steps to get started.
* [Install Scala](https://www.scala-lang.org/download/), we recommend working with sbt, together with [Metals](https://scalameta.org/metals/docs/editors/vscode/) on VS Code. 
* Clone the repository `git clone https://github.com/jonasspenger/pods-workflows.git`.
* Compile the project `sbt compile`, run all tests `sbt test`, or execute a main class for the project `sbt run <argument>*`.

## Design Documents
To find out more about Portals Workflows and the programming model, please visit the [Design Documents](design) page. There you will find a walkthrough through the model.

## Examples
Example programs can be found in the [Examples](examples) page. Note that not all of the examples are executable yet, but will be later on. Executable examples can also be found in the [examples](src/main/scala/pods/workflows/examples) directory.

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
  1. Fork the repo.
  1. Implement and test your implementation, add documentation.
  1. [Group/squash commit](https://github.com/akka/akka/blob/main/CONTRIBUTING.md#creating-commits-and-writing-commit-messages).
  1. Submit a pull-request (include a reference to the issue).
  1. After review and approval (LGTM) the pull-request will be merged. 
