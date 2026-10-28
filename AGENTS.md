# AGENTS.md

## Project

sparta-hss is sipgate's open-source 3GPP Home Subscriber Server (HSS):
subscriber authentication (Milenage AKA), subscription data, and location
management over Diameter — Cx/Dx (IMS), S6a/S6d (4G/EPC) and SWx (VoWiFi).

- Java 25, Spring Boot, Maven multi-module build; both modules are published
  to Maven Central
- `sparta-hss-base` — the core library: Diameter handlers, AKA engine, JPA
  entities/DAOs, services, extension points
- `sparta-hss-spring-boot` — the runnable application and auto-configuration
  on top of the base; also the source of the Docker image
- `docs/` — MkDocs Material site, published to GitHub Pages

The docs are the source of truth for behavior: read the page describing what
you are about to change before changing it, and update it in the same change
when behavior changes.

## Build and test

```shell
./mvnw clean verify                                    # unit + Spring integration tests (fast)
make run-e2e-tests                                     # containerized Diameter E2E suite (needs Docker)
make run-e2e-single TEST='CxE2eTest$$Sar'              # single E2E class

# one test class; the flag is required because -Dtest applies to both modules
./mvnw test -Dtest=SpartaHssApplicationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Only the E2E suite (`@Tag("E2E")`) is excluded from `./mvnw verify`. Test tiers:
[docs/development/testing.md](docs/development/testing.md).

## Documentation

Setup: [docs/development/local-development.md](docs/development/local-development.md).
Build the way CI does — `--strict` fails on broken links and anchors, so fix
references in the same change that breaks them:

```shell
NO_MKDOCS_2_WARNING=1 mkdocs build --strict --config-file docs/mkdocs.yml
```

python-markdown pitfalls that mangle pages: fenced code blocks indented inside
a list item are not rendered as code, and nested bullet lists flatten. Avoid
both; verify suspicious pages in the built `site/` HTML.

## Contributing

Follow [CONTRIBUTING.md](CONTRIBUTING.md) (commit format, tests, Javadoc,
`.editorconfig`). Pull-request CI additionally builds the `-P deploy` profile,
the Docker image, the E2E suite and the strict docs build.

Every pull request you open MUST end its description with this exact line:

```text
AI generated. Remove this warning once you as a human have reviewed the PR description.
```

Every issue you file MUST end its body with the matching line:

```text
AI generated. Remove this warning once you as a human have reviewed the issue.
```

Never remove or alter either line yourself — the human reviewer removes it after
reviewing the text.

## Non-obvious behavior

- `sipgate.diameter.capabilities` gates which Diameter handlers are registered
  and which applications are advertised; a missing capability also leaves the
  supporting beans un-instantiated. The code default is `Cx/Dx`, `S6a/S6d`; the
  shipped `application.yaml` adds `SWx`.
- Outbound Diameter requests (ISD/CLR/RTR) are fire-and-forget, sent by event
  listeners off the inbound request thread — a deliberate deviation from the
  spec's sequencing. See docs/development/architecture.md.
- The ISD push from the profile batch endpoints is throttled to one ISD per
  500 ms over the whole batch, not per subscriber.
