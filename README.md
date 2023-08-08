# Portals

[![Build Status](https://github.com/portals-project/portals/actions/workflows/build-test.yaml/badge.svg)](https://github.com/portals-project/portals/actions/workflows/build-test.yaml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/portals-project/portals/blob/main/LICENSE)
[![API Docs](https://img.shields.io/badge/API_Docs-orange)](https://portals-project.org/api/)
[![Portals Website](https://img.shields.io/badge/Portals_Website-teal)](https://portals-project.org/)
[![Portals Playground](https://img.shields.io/badge/Portals_Playground-teal)](https://portals-project.org/playground/)

## Project Information

Portals is a framework written in Scala under the Apache 2.0 License for stateful serverless applications.

The Portals framework aims to unify the distributed dataflow streaming model with the actor model, providing flexibility, data-parallel processing capabilities and strong guarantees. The framework is designed to be used in a serverless environment, where the user can focus on the business logic of the application, while the framework takes care of the infrastructure and failure management.

Among the key features of Portals are:

* Multi-dataflow applications. Portals allows the user to define multiple dataflows, which can be connected to each other. This allows the user to define complex servless applications composed of multiple dataflows (microservices).
* Inter-dataflow services. Portals introduces a new abstraction: the Portal service. The Portal service abstraction allows dataflows to expose and connect to services, and enables an actor-like communication pattern between the dataflows integrated with a futures API.
* Decentralized execution on cloud and edge. The runtime is designed to be decentralized; the API provides primitives for connecting to other runtimes, allowing the user to deploy the application on multiple nodes, including edge devices.

Find out more about Portals at [https://portals-project.org](https://portals-project.org).

> **Disclaimer**
> Portals is a research project under development and not yet ready for production use.

## Project Status and Roadmap

The Portals project is currently in the early stages of development, we are currently working towards a first release. To give an idea of the project status, we provide a list of features which will be included for the first release, and highlight features which are yet to be implemented with an asterisk (*).

* Portals Core API. The Portals Core API provides basic abstractions for defining multi dataflow applications and Portal services.
* PortalsJS API. The PortalsJS API provides a JavaScript API for writing Portals applications. It is also used within the context of the [Playground](https://portals-project.org/playground/).
* Portals Interpreter. The Portals Interpreter is a runtime for running Portals applications locally and for testing applications.
* Portals Examples. There are several examples and use cases available in the examples directory, which show how to build Portals applications.
* Portals Benchmark. The Portals Benchmark features some microbenchmarks for testing the performance of Portals applications. Due to the recent restructuring of the project, the benchmark is currently not available, and is currently under development.
* \*Portals Libraries. There are two Portals Libraries, one SQL library for exposing SQL queries as Portal services, and one Actor library for writing actor programs on Portals. These are currently under development.

The following features are planned for the next releases, with target to be finished by the end of 2023.

* \* Portals Runtime. A fault-tolerant, elastically scalable runtime for running Portals applications on multiple nodes across edge and cloud.

> **Note**
> Features that are currently in development are marked as *experimental* and are likely to change.

## Project Setup

To use Portals in your project, add the following dependecy to your `build.sbt` file:

```scala
libraryDependencies += "org.portals-project" %% "portals" % "0.1.0-RC1"
```

A full project setup with instructions for executing a hello world example is available at [https://github.com/portals-project/Hello-World](https://github.com/portals-project/Hello-World).

## Getting Started Guide

We recommend the following steps to get started.
* [Install Scala](https://www.scala-lang.org/download/), we recommend working with sbt, together with [Metals](https://scalameta.org/metals/docs/editors/vscode/) on VS Code. 
* Clone the [Hello World](https://github.com/portals-project/Hello-World) repository.
* Compile and run the project `sbt compile;`, `sbt run;`.
* To get some inspiration, check out the [examples](/portals-examples) or read the [tutorial](https://www.portals-project.org/tutorial).

### Examples

The Portals library comes with an API for defining multi-dataflow applications, and a serverless runtime for executing these applications. The most basic example would involve defining a `workflow` and a `generator` within the context of a `PortalsApp`, and executing this on the test runtime interpreter. 

```scala
import portals.api.dsl.DSL.*
import portals.system.Systems

object HelloWorld extends App:
  val app = PortalsApp("HelloWorld"):

    val generator = Generators.fromList(List("Hello World!"))

    val workflow = Workflows[String, String]()
      .source(generator.stream)
      .map(_.toUpperCase())
      .logger()
      .sink()
      .freeze()
  
  val system = Systems.test()
  system.launch(app)
  system.stepUntilComplete()
  system.shutdown()
```

As the example shows, to write applications you need to import the API from the DSL, and to run the applications you need a system. But, there are many more abstractions and concepts in Portals. The main abstractions of the Portals API are the following:
* Workflows: for processing atomic streams.
* Per-key stateful tasks: the processing units within workflows are stateful tasks sharded over a key.
* Generators: for generating atomic streams.
* Sequencers: for sequencing atomic streams.
* Splitters: for splitting atomic streams.
* Portals: for exposing services with a futures API.
* Registry: for connecting to streams and portals in other applications.

With these abstractions, you can define complex multi-dataflow applications, and execute them on the serverless runtime. For more examples, please check out the [examples](/portals-examples) directory, or the [tutorial](https://www.portals-project.org/tutorial).

## Support and Contact

For help or questions, contact the Portals developers and community on the [Portals Google Groups](https://groups.google.com/g/portals-project) mailing list.

If you find a bug in Portals, then [open an issue](https://github.com/portals-project/portals/issues).

For other private questions, please refer to the contact information of the [core team](https://www.portals-project.org/team).

## Comparison to Other Projects

TODO: Flink; Kafka; Durable Functions; https://github.com/typelevel/feral; Kalix

## Project Structure

The Portals framework is licensed under Apache 2.0 and is maintained by the [Portals Project Committee](https://www.portals-project.org/team).

If you are interested in contributing to the project, please check out our [contributing guidelines](CONTRIBUTING.md).

## Cite Our Work

If you want to cite our work, please consider citing the following publication:

* Jonas Spenger, Paris Carbone, and Philipp Haller. 2022. Portals: An Extension of Dataflow Streaming for Stateful Serverless. In Proceedings of the 2022 ACM SIGPLAN International Symposium on New Ideas, New Paradigms, and Reflections on Programming and Software (Onward! ’22), December 8-10, 2022, Auckland, New Zealand. ACM, New York, NY, USA, 19 pages. https://doi.org/10.1145/3563835.3567664

```bibtex
@inproceedings{SpengerCH22,
  author = {Jonas Spenger and Paris Carbone and Philipp Haller},
  title = {Portals: An Extension of Dataflow Streaming for Stateful Serverless},
  year = {2022},
  booktitle = {Proceedings of the 2022 {ACM} {SIGPLAN} International Symposium on New Ideas, New Paradigms, and Reflections on Programming and Software, Onward! 2022, Auckland, New Zealand, December 8-10, 2022},
  editor = {Christophe Scholliers and Jeremy Singer},
  pages = {153--171},
  publisher = {Association for Computing Machinery},
  address = {New York, NY, USA},
  url = {https://doi.org/10.1145/3563835.3567664},
  doi = {10.1145/3563835.3567664},
  isbn = {9781450399098},
  location = {Auckland, New Zealand},
  series = {Onward! 2022},
}
```
