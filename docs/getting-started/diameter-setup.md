# Diameter setup

The HSS is a **Diameter client**: on startup it opens outbound connections to the
configured peers and registers one handler per supported request type. It never
listens for inbound Diameter.

## DRA

In practice the HSS connects to a **DRA**, and that is the only setup that has been
tested. Connecting the HSS directly to other nodes (e.g. an MME or S-CSCF) has never
been exercised and may or may not work — feedback on that is welcome, as are PRs.

Transport-wise, only **TCP** is supported. No SCTP, no TLS.

## Interface completeness

The Diameter interfaces may be **incomplete**: sparta-hss only implements the
messages that were needed for sipgate's own core network. For Cx, for example,
UAR/UAA is missing because the sipgate IMS core handles IMS authorization in a
different way. If you need more spec compliance, PRs are welcome.

## Identity

| Property                          | Required | Description                                        |
|-----------------------------------|----------|----------------------------------------------------|
| `sipgate.diameter.origin-host`    | yes      | The HSS's Origin-Host, e.g. `hss.example.com`      |
| `sipgate.diameter.origin-realm`   | yes      | The HSS's Origin-Realm, e.g. `example.com`         |

Both have placeholder defaults that fail fast at the wire level: with them unset the
container starts, but the node connects nowhere and `/health/ready` stays `DOWN`.

## Peers

The HSS connects to every configured peer and stays up even if some of them are
unreachable; it reconnects with the configured delay.

```yaml
sipgate:
  diameter:
    peers:
      - host: dra-1.example.com
        port: 3868
      - host: dra-2.example.com
        port: 3868
```

| Property                              | Default | Description                              |
|---------------------------------------|---------|------------------------------------------|
| `sipgate.diameter.peers[N].host`      | —       | Hostname or address of the peer          |
| `sipgate.diameter.peers[N].port`      | `3868`  | Diameter port of the peer                |
| `sipgate.diameter.reconnect-delay`    | `PT30S` | Delay between reconnect attempts         |
| `sipgate.diameter.watchdog-interval`  | `PT30S` | Interval of the connection watchdog      |
| `sipgate.diameter.host-ip`            | local   | Local address to bind; defaults to the local host address |

## Capabilities

`sipgate.diameter.capabilities` decides **which Diameter applications the HSS
advertises in its CER — and which parts of the HSS are available at all**. An
interface that is not listed is not available at runtime in that deployment: its
handlers are not loaded.

| Value     | Interface | Commands served              |
|-----------|-----------|------------------------------|
| `Cx/Dx`   | Cx/Dx     | SAR, MAR (+ outbound RTR)    |
| `S6a/S6d` | S6a/S6d   | AIR, ULR, PUR, NOR (+ outbound CLR, ISD) |
| `SWx`     | SWx       | SAR, MAR                     |

The shipped `application.yaml` enables all three; the code-level default (when the
property is absent, e.g. when embedding the core) is `Cx/Dx` and `S6a/S6d`.

In container deployments, set the capability via the environment variable rather
than by overriding `application.yaml`:

```shell
docker run -e SIPGATE_DIAMETER_CAPABILITIES=Cx/Dx,S6a/S6d ...
```

For non-containerized deployments, use whatever configuration mechanism your
application uses.
