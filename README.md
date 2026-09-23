# sparta-hss

The open-source release of sipgate's Home Subscriber Server (HSS) for 3GPP mobile
networks: subscriber authentication (Milenage AKA), subscription data, and location
management over Diameter — Cx/Dx (IMS), S6a/S6d (4G/EPC) and SWx (VoWiFi).

## Documentation

The full documentation lives in [`docs/`](docs/index.md) and is published to
[GitHub Pages](https://sipgate.github.io/sparta-hss-foss/).

- [Quickstart](docs/getting-started/quickstart.md) — run the Docker image
- [Configuration](docs/reference/configuration.md), [HTTP API](docs/reference/http-api.md),
  [Metrics](docs/reference/metrics.md), [Database](docs/reference/database.md)
- [Subscriber profiles](docs/concepts/profiles.md), [Roaming](docs/concepts/roaming.md),
  [Extending the core](docs/development/extending.md)

## Quickstart

Images are published to
[`sipgategmbh/sparta-hss-foss`](https://hub.docker.com/r/sipgategmbh/sparta-hss-foss)
(`linux/amd64` and `linux/arm64`; `:<version>`, `:latest`, `:<version>-SNAPSHOT`).
The Diameter peer configuration has no usable default — set at least these three
variables, or `/health/ready` stays `DOWN`:

```shell
docker run \
  -e SIPGATE_DIAMETER_ORIGIN_HOST=hss.example.com \
  -e SIPGATE_DIAMETER_ORIGIN_REALM=example.com \
  -e SIPGATE_DIAMETER_PEERS_0_HOST=dra.example.com \
  -p 8080:8080 \
  -v sparta-hss-data:/var/lib/sparta-hss \
  sipgategmbh/sparta-hss-foss:latest
```

See the [quickstart](docs/getting-started/quickstart.md) for the full variable list,
configuration mounts and verification steps.

## Building

Java 25 and Maven (a wrapper is included):

```shell
./mvnw clean verify        # build + unit tests
make run-e2e-tests         # containerized E2E suite
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md), [CHANGELOG.md](CHANGELOG.md),
[RELEASING.md](RELEASING.md) and [SECURITY.md](SECURITY.md).
