# Pods Workflows
Welcome to the Pods Workflows repo!

This is a fresh start, to find the old repo look through the branches for [!old-dump](https://github.com/jonasspenger/pods-workflows/tree/!old-dump).

## Getting Started Guide
We recommend the following steps to get started.
* [Install Scala](https://www.scala-lang.org/download/), we recommend working with sbt, together with [Metals](https://scalameta.org/metals/docs/editors/vscode/) on VS Code. 
* Clone the repository `git clone https://github.com/jonasspenger/pods-workflows.git`.
* Compile the project `sbt compile`, run all tests `sbt test`, or execute a main class for the project `sbt run <argument>*`.

## Design Documents
To find out more about Pods Workflows and the programming model, please visit the [Design Documents](design) page. There you will find a walkthrough through the model.

## Examples
Example programs can be found in the [Examples](examples) page. Note that not all of the examples are executable yet, but will be later on. Executable examples can also be found in the [examples](src/main/scala/pods/workflows/examples) directory.

## Milestones
* [X] Create readme and start onboarding process. 
* [ ] Create a description of the programming model.
* [ ] Create an implementation-design document.
* [ ] Define the interfaces and traits.
* [ ] Write the use-cases and examples that we want to execute.
  * [ ] Killer application: something with microservices and serializable updates.
  * [ ] Serializable updates demonstration.
  * [ ] Common examples of stateful serverless: Shopping Cart; Bank Account; Social Media; Microservices.
  * [ ] Other programming models embedded in Pods Workflows.
    * [ ] MapReduce
    * [ ] BSP
    * [ ] Pregel/Menthor
    * [ ] Actors
    * [ ] Dataflow/Streaming
    * [ ] Dataframes
* [ ] Create test suite.
* [ ] Implement / make the examples executable.
* [ ] Perform evaluation.
* [ ] Create a release.

## Milestones for [Onward!](https://2022.splashcon.org/track/splash-2022-Onward-papers) 22 Paper
* [X] Write *introduction*
* [X] Write *contributions* 
* [ ] (06.06) Describe Challenges
* [ ] (06.13) Describe Programming Model
* [ ] Operational Semantics
* [ ] Evaluation
* [ ] Abstract, Conclusion (Paris, Philipp)
* [ ] (06.16) First Draft
* [ ] (07.15) Final Draft

## Contributing
Contributions are welcome! To contribute please follow these guideines.
* Familiarize yourself with the technologies and concepts.
  * Scala 3: The [Scala 3 docs](https://docs.scala-lang.org/) is a great resource. 
  * Actors: [Akka Typed Actors](https://doc.akka.io/docs/akka/2.5.32/typed/index.html).
  * Dataflow: [Flink](https://github.com/ververica/flink-training) and more [Flink](https://flink.apache.org/).
  * Stateful serverless: [Durable Functions](https://docs.microsoft.com/en-us/azure/azure-functions/durable/) and [Flink StateFun](https://nightlies.apache.org/flink/flink-statefun-docs-master/).
  * Other projects: [Reactors](http://reactors.io/).
* To contribute we follow the [Akka guidelines](https://github.com/akka/akka/blob/main/CONTRIBUTING.md):
  1. Choose an issue (or create an issue).
  1. Fork the repo.
  1. Implement and test your implementation, add documentation.
  1. [Group/squash commit](https://github.com/akka/akka/blob/main/CONTRIBUTING.md#creating-commits-and-writing-commit-messages).
  1. Submit a pull-request (include a reference to the issue).
  1. After review and approval (LGTM) the pull-request will be merged. 
