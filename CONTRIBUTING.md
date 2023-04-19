# Contributing

Contributions are welcome! To contribute please follow these guideines.
1. Choose an issue (or create an issue).
2. Fork the repo.
3. Create a branch of the fork and work on the issue.
4. Implement and test your implementation, add documentation.
    1. Add documentation to the docs/tutorial for any new features.
    2. Compile the code with `sbt compile`.
    2. Format the code with scalafmt: `sbt scalafmtAll; sbt scalafmtSbt;`.
5. Verify that the code tests and formatting tests pass: `sbt test; sbt scalafmtCheckAll; sbt scalafmtSbtCheck;`.
6. Group/squash commit, with a useful commit message (`feat: ...`, `fix: ...`, https://www.conventionalcommits.org/en/v1.0.0/). 
7. Submit a pull-request (include a reference to the issue).
8. After review and approval (LGTM) the pull-request will be merged. 

## External Dependencies
All external dependencies must have a Apache 2.0 compatible license.

## Pull Request Requirements
* All code must be formatted tested, documented, and formatted well.
* The commit messages should describe the changes: group/squash commit with a useful commit message (`feat: ...`, `fix: ...`, https://www.conventionalcommits.org/en/v1.0.0/)
* The pull request should be descriptive, include a summary of the changes, and include a reference to the issue.