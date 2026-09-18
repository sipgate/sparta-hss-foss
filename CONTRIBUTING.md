# Contributing

Thanks for your interest in sparta-hss. This document covers what you need to build the
project and what reviewers will look for.

## Build and test

The project builds with Java 25 and the bundled Maven wrapper:

```shell
./mvnw clean verify
```

Before opening a release-affecting change, confirm the publishing profile still builds
(sources and javadoc jars; signing is skipped because it needs a key):

```shell
./mvnw -P deploy clean package -DskipTests
```

## Code conventions

- Use descriptive variable names; prefer readable over clever code.
- Cover your change with tests; for bug fixes that means a regression test that reproduces
  the bug. Not everything warrants a unit test — POJOs and configuration classes generally
  do not.
- Keep Javadoc in sync with any method or type you change.

Formatting follows [`.editorconfig`](.editorconfig) (LF, UTF-8, 120 columns, 4-space Java)
but is not enforced by the build; please configure your editor to respect it.

## Commits and pull requests

- [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) with a scope in brackets:
  `fix(cx): reject SAR on unknown user`.
- Subject at most 50 characters, body wrapped at 72.
- Explain **why** in the body; the diff already shows what.
- Small, atomic commits. Each one should make sense on its own and leave the build working.

Open the pull request against `main`. CI runs the checks defined in
[verify-pull-request.yml](.github/workflows/verify-pull-request.yml) — a `./mvnw clean verify`
build plus a `-P deploy` build that produces the sources and javadoc jars — all must pass.
Describe what changed and why.

## Reporting bugs

Please do not file security issues as public issues — see [SECURITY.md](SECURITY.md).
