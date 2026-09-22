# Releasing

Maintainers cut releases with [Release: cut a new version](.github/workflows/release.yml) — *Run workflow*
on `main`, giving it the version to publish (`1.1.0`) and the version `main` continues on
(`1.1.1-SNAPSHOT`).

It handles:

- **`CHANGELOG.md`** — moves `[Unreleased]` into a dated section and opens a fresh one. Fails if
  `[Unreleased]` is empty.
- **`pom.xml`** — sets the release version across all modules, and the development version afterwards.
- **git tag** — annotated tag on the release commit.
- **publishing** — pushing the tag triggers
  [publish-release.yml](.github/workflows/publish-release.yml) for Maven Central and
  [docker-image.yml](.github/workflows/docker-image.yml) for Docker Hub.
