# Quickstart

Run sparta-hss from the published Docker image and connect it to a Diameter peer.

## Images

Images are published to
[`sipgategmbh/sparta-hss-foss`](https://hub.docker.com/r/sipgategmbh/sparta-hss-foss) for
`linux/amd64` and `linux/arm64`. Tags: `:<version>`, `:latest` (follows the most recent
release), and `:<version>-SNAPSHOT` (every build of `main`).

## Run

The HSS needs to know who it is and where to connect. Of all configuration
properties, only these need to be provided:

| Environment variable            | Maps to                          | Example             |
|---------------------------------|----------------------------------|---------------------|
| `SIPGATE_DIAMETER_ORIGIN_HOST`  | `sipgate.diameter.origin-host`   | `hss.example.com`   |
| `SIPGATE_DIAMETER_ORIGIN_REALM` | `sipgate.diameter.origin-realm`  | `example.com`       |
| `SIPGATE_DIAMETER_PEERS_0_HOST` | `sipgate.diameter.peers[0].host` | `dra.example.com`   |

The peer port defaults to `3868` (override with `SIPGATE_DIAMETER_PEERS_0_PORT`),
and every other property has a working default. Without these, the container starts
and serves `/health/live`, but it never connects to a peer and `/health/ready` stays
`DOWN`.

```shell
docker run \
  -e SIPGATE_DIAMETER_ORIGIN_HOST=hss.example.com \
  -e SIPGATE_DIAMETER_ORIGIN_REALM=example.com \
  -e SIPGATE_DIAMETER_PEERS_0_HOST=dra.example.com \
  -e SIPGATE_DIAMETER_CAPABILITIES=Cx/Dx,S6a/S6d \
  -p 8080:8080 \
  -v sparta-hss-data:/var/lib/sparta-hss \
  sipgategmbh/sparta-hss-foss:latest
```

The `SIPGATE_DIAMETER_CAPABILITIES` line is shown for illustration — it is not
required, the default already enables all three interfaces. Any other property from
the [configuration reference](../reference/configuration.md) can be overridden the
same way.

## Verify

```shell
curl -sf localhost:8080/health/live    # 200 as soon as the app is up
curl -sf localhost:8080/health/ready   # 200 once a Diameter peer connection is established
```

`/health/ready` includes the database and the Diameter peer state — it is the
endpoint to use in load-balancer and orchestrator health checks. See
[Monitoring](../guides/monitoring.md) for details.

## Next steps

- [Deployment](deployment.md) — mount the subscriber-facing configuration, keep the
  database persistent, and point production at a real database
- [Diameter setup](diameter-setup.md) — peers, capabilities and the behaviour of the
  Diameter connection in detail
