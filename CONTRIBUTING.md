# Contributing
We warmly welcome contributions to our project! Please follow these guidelines to make the process as smooth as possible.

## Getting Started
* Find an Issue: Look for an existing issue that matches your interest or create a new one.
* Fork the Repository: Create a fork of the repository to your personal GitHub account.
* Create a Branch: Create a new branch from the forked repository to work on the issue.
* Implement Your Changes: Code your solution, add necessary tests, and update documentation. You might need to do the following:
    * Add new tests.
    * Add doc comments to public APIs.
    * Update the docs/tutorial.
    * Format the code with scalafmt: `sbt scalafmtAll`; `sbt scalafmtSbt`;.
* Verify Your Code: Run `sbt test`; `sbt scalafmtCheckAll`; `sbt scalafmtSbtCheck`; to ensure everything passes.
* Prepare for Submission: Group/squash your commits and write a [useful commit message](#useful-commit-messages).
* Submit a Pull Request: Follow the [PR Template](#pr-template).

## Useful Commit Messages
Commit messages should clearly communicate the changes. Here's how to create effective messages:

* Keep the Subject Line Short and Informative: Provide a brief summary of the change. Keep it under 50 characters.
* Use the Imperative Form: Write messages as commands, e.g., "Fix bug in X" rather than "Fixes bug in X" or "Fixed bug in X."
* Use Bullet Points: Structure the message with bullet points for easy readability.
* Reference Relevant Issues: Include references to issues if applicable (e.g., "Resolves #123").
* Be Specific: Avoid vague messages. Stay away from vague or generic messages like "Update file" or "Fix bug." Explain what was changed and why.
* Include Impact of Changes: If applicable, describe how the changes impact other parts of the system.

## PR Template
```
Descriptive PR Title: [Provide a short and informative title that clearly conveys the essence of the change]

# Summary of Changes:
[Include a detailed summary that explains the modifications and their purpose. This can be a few sentences or bullet points, as needed.]

# Related Issues:
[Link to or mention relevant issues, if applicable. Use "Resolves #123" or similar syntax to auto-close related issues upon merge.]

# Checklist:
- [ ] The code is documented, and formatted using scalafmt.
- [ ] Testing: The code is appropriately tested.
- [ ] Verification Checks: The code passes the verification checks: `sbt test`; `sbt scalafmtCheckAll`; `sbt scalafmtSbtCheck`.

[More details can be added here as needed.]
```

## After Submission
* Await Review: Your pull request will be reviewed by the maintainers.
* Address Feedback if Needed: Make necessary changes based on feedback.
  * Ask for a re-review: use the *Re-request review* button to notify the reviewers that you have addressed their feedback.
  * Address subsequent feedback and re-request review as needed.
  * Group/squash your commits for a clean history before the final merge.
* Merge: Once approved (LGTM), your pull request will be merged into the main codebase.

## External Dependencies
All external dependencies must have a Apache 2.0 compatible license.

## Releases, Tags, Branches
TODO

## Useful Commands
```bash
# Format Code
sbt scalafmtAll;
sbt scalafmtSbt;
# Portals Tests/Checks
sbt test;
sbt doc;
sbt scalafmtCheckAll; 
sbt scalafmtSbtCheck;
# PortalsJS Tests/Checks
sh scripts/portalsjs/portalsjs-compile.sh;
sh scripts/portalsjs/portalsjs-test-runner.sh;
```
