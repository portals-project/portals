# Portals

[![Build Status](https://github.com/portals-project/portals/actions/workflows/build-test.yaml/badge.svg)](https://github.com/portals-project/portals/actions/workflows/build-test.yaml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/portals-project/portals/blob/main/LICENSE)
[![API Docs](https://img.shields.io/badge/docs-API_Docs-orange)](https://portals-project.org/api/)
[![GitHub issues](https://img.shields.io/badge/issues-Github_Issues-orange)](https://github.com/portals-project/portals/issues)
[![Support](https://img.shields.io/badge/chat-Google_Groups-orange)](https://groups.google.com/g/portals-project)
[![Website](https://img.shields.io/badge/Website-yellow)](https://portals-project.org/)
[![Playground](https://img.shields.io/badge/Playground-yellow)](https://portals-project.org/playground/)

## Project Information

Portals is a framework written in Scala under the **Apache 2.0 License** for stateful serverless applications.

The Portals framework aims to unify the distributed dataflow streaming model with the actor model, providing flexibility, data-parallel processing capabilities and strong guarantees. The framework is designed to be used in a serverless environment, where the user can focus on the business logic of the application, while the framework takes care of the infrastructure and failure management.

Key features:
* Multi-dataflow applications: define, connect and compose multiple dataflows into complex services.
* Inter-dataflow services, the Portal service abstraction: expose/connect to/from dataflows as services.
* Decentralized cloud/edge execution: API primitives for connecting runtimes, and deploying on edge/cloud devices.

Find out more about Portals at [https://portals-project.org](https://portals-project.org).

> **Note**
> Disclaimer: Portals is a research project under development and not yet ready for production use.

## Project Status and Roadmap

The Portals project is currently in the early stages of development. We are working towards a first release, which in addition to the current state, will include a distributed, decentralized runtime. We have planned a release for this fall 2023. Besides these new developments, we have a stable Scala API, JS API, Interpreter, Benchmarks, and Examples.

> **Note**
> Features that are currently in development are marked as *experimental* and are likely to change.

## Project Setup

To use Portals in your project, add the following dependecy to your `build.sbt` file:

```scala
libraryDependencies += "org.portals-project" %% "portals" % "0.1.0-RC1"
```

A full project setup with instructions for executing a hello world example is available at [https://github.com/portals-project/Hello-World](https://github.com/portals-project/Hello-World).

> **Note**
> Portals has not yet been published to Maven Central. The Portals Project can be published locally using the `sbt publishLocal` command. To use Portals in your project, import the local snapshot instead: `libraryDependencies += "org.portals-project" %% "portals-core" % "0.1.0-SNAPSHOT"`.

## Getting Started Guide

We recommend the following steps to get started.
* [Install Scala](https://www.scala-lang.org/download/), we recommend working with sbt, together with [Metals](https://scalameta.org/metals/docs/editors/vscode/) on VS Code. 
* Clone the [Hello World](https://github.com/portals-project/Hello-World) repository.
* Compile and run the project `sbt compile;`, `sbt run;`.
* To get some inspiration, check out the [examples](/portals-examples) or read the [tutorial](https://www.portals-project.org/learn/tutorial).

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

Check out an extensive [Tutorial](https://www.portals-project.org/learn/tutorial) and the [Examples Directory](/portals-examples) for more examples.

## Support and Contact

For help or questions, contact the Portals developers and community on the [Portals Google Groups](https://groups.google.com/g/portals-project) mailing list.

If you find a bug in Portals, then [open an issue](https://github.com/portals-project/portals/issues).

## Contributing

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
