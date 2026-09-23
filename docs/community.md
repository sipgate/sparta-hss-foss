# Contributing and releases

The maintainer-facing documentation lives at the repository root, next to the code it
describes.

| Document | Purpose                                                          |
|----------|------------------------------------------------------------------|
| [CONTRIBUTING.md](https://github.com/sipgate/sparta-hss-foss/blob/main/CONTRIBUTING.md) | Build/test commands, code conventions, commit and PR policy |
| [CHANGELOG.md](https://github.com/sipgate/sparta-hss-foss/blob/main/CHANGELOG.md) | Release history (Keep a Changelog format)            |
| [RELEASING.md](https://github.com/sipgate/sparta-hss-foss/blob/main/RELEASING.md) | How maintainers cut releases                       |
| [SECURITY.md](https://github.com/sipgate/sparta-hss-foss/blob/main/SECURITY.md) | How to report a vulnerability                      |

Issues and pull requests are managed on
[GitHub](https://github.com/sipgate/sparta-hss-foss).

Releases are published to:

- **Docker Hub** — [`sipgategmbh/sparta-hss-foss`](https://hub.docker.com/r/sipgategmbh/sparta-hss-foss)
  (`linux/amd64` and `linux/arm64`; `:<version>`, `:latest`, `:<version>-SNAPSHOT`)
- **Maven Central** — `com.sipgate:sparta-hss-base` and `com.sipgate:sparta-hss-spring-boot`
  (plus sources and Javadoc jars)
