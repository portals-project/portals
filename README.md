# Portals

[![Build Status](https://github.com/portals-project/portals/actions/workflows/build-test.yaml/badge.svg)](https://github.com/portals-project/portals/actions/workflows/build-test.yaml)

Welcome to the [Portals](https://www.portals-project.org/) repo!

## Getting Started Guide
We recommend the following steps to get started.
* [Install Scala](https://www.scala-lang.org/download/), we recommend working with sbt, together with [Metals](https://scalameta.org/metals/docs/editors/vscode/) on VS Code. 
* Clone the repository `git clone https://github.com/portals-project/portals.git`.
* Compile the project `sbt compile`.
* Check out the [examples](examples/src/main/scala/portals/examples) or [tests](core/src/test/scala/portals).
* Start [contributing](CONTRIBUTING.md).

## Examples
Examples can be found in the [examples](examples/src/main/scala/portals/examples) directory. You can run an example by running the command `sbt examples/run`.

## Tests
The tests are located in the [test](core/src/test/scala/portals) directory. You can run the tests by running the command `sbt test`.

## References

If you want to cite our work, please consider citing the following publication:

* Jonas Spenger, Paris Carbone, and Philipp Haller. 2022. Portals: An Extension of Dataflow Streaming for Stateful Serverless. In Proceedings of the 2022 ACM SIGPLAN International Symposium on New Ideas, New Paradigms, and Reflections on Programming and Software (Onward! ’22), December 8ś10, 2022, Auckland, New Zealand. ACM, New York, NY, USA, 19 pages. https://doi.org/10.1145/3563835.3567664

```bib
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
