# Local development

## Prerequisites

- Java 25 (the build targets `--release 25`)
- Maven — a wrapper (`./mvnw`) is included, so no local installation is needed
- Docker (only for the [E2E tests](testing.md#e2e))

A `.sdkmanrc` pins Java 25.0.1-zulu and Maven 3.9.11 for sdkman users.

## Build and test

```shell
./mvnw clean verify        # build + unit and Spring integration tests (fast)
make run-e2e-tests         # containerized E2E suite

# a single test class (across the two-module reactor)
./mvnw test -Dtest=SpartaHssApplicationTest -Dsurefire.failIfNoSpecifiedTests=false
```

See [Testing](testing.md) for the tiers.

## Run the application

Create a Run Configuration for `com.sipgate.sparta.hss.SpartaHssApplication` in your
IDE. The test resources (`sparta-hss-spring-boot/src/test/resources/application.yaml`)
show a working local setup: in-memory H2 database and local profile files.

Minimum required properties for a real run:

```yaml
sipgate:
  diameter:
    origin-host: hss.local
    origin-realm: local
    peers:
      - host: <peer-host>
        port: 3868
  profileDir: ./profiles
  ims:
    userProfile:
      path: ./user-profile.xml
```

## Serving the documentation

This site is MkDocs Material. For a live-reload dev server from the repository root:

```shell
python3 -m venv docs/.venv
docs/.venv/bin/pip install -r docs/requirements.txt
docs/.venv/bin/mkdocs serve --config-file docs/mkdocs.yml
```

Then open http://localhost:8000 — pages reload on save. If MkDocs is already
installed, `mkdocs serve --config-file docs/mkdocs.yml` alone is enough.

Material for MkDocs prints an informational warning about the upcoming MkDocs 2.0 on
every build (see the [Material blog post](https://squidfunk.github.io/mkdocs-material/blog/2026/02/18/mkdocs-2.0/));
set `NO_MKDOCS_2_WARNING=1` to silence it.

A static build (`mkdocs build --config-file docs/mkdocs.yml`) writes to `site/` at
the repository root (gitignored); that is what the Pages workflow deploys.
