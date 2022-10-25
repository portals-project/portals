# Contributing

Contributions are welcome! To contribute please follow these guideines.
  1. Choose an issue (or create an issue).
  2. Fork the repo.
  3. Implement and test your implementation, add documentation.
  4. Format the code by running `sbt scalafmt` (and `sbt scalafmtAll` to format all files, incl. test and sbt `sbt scalafmtSbt`) (`sbt scalafmtAll; sbt scalafmtSbt;`).
  5. Check that the tests pass `sbt test`, and that the formatting is correct `sbt scalafmtCheckAll` and `sbt scalafmtSbtCheck` (`sbt test; sbt scalafmtCheckAll; sbt scalafmtSbtCheck;`).
  6. Group/squash commit.
  7. Submit a pull-request (include a reference to the issue).
  8. After review and approval (LGTM) the pull-request will be merged. 

 For more information, we refer to the [Akka guidelines](https://github.com/akka/akka/blob/main/CONTRIBUTING.md) for contributing.
