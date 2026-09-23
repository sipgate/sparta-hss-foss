# Configuration

All configuration follows Spring Boot conventions: properties live in
`application.yaml` (or `SPRING_CONFIG_LOCATION`), and every property can be
overridden with an environment variable using relaxed binding — `sipgate.diameter.origin-host`
becomes `SIPGATE_DIAMETER_ORIGIN_HOST`, `sipgate.diameter.peers[0].host` becomes
`SIPGATE_DIAMETER_PEERS_0_HOST`.

The Spring Boot module ships batteries-included defaults; an application built on top
of the core replaces them entirely.

## Diameter

| Property                            | Default   | Environment variable                   | Description |
|-------------------------------------|-----------|----------------------------------------|-------------|
| `sipgate.diameter.origin-host`      | *placeholder* (must be set) | `SIPGATE_DIAMETER_ORIGIN_HOST`      | The HSS's Diameter Origin-Host |
| `sipgate.diameter.origin-realm`     | *placeholder* (must be set) | `SIPGATE_DIAMETER_ORIGIN_REALM`     | The HSS's Diameter Origin-Realm |
| `sipgate.diameter.peers[N].host`    | —         | `SIPGATE_DIAMETER_PEERS_0_HOST`        | Host of a Diameter peer (DRA) |
| `sipgate.diameter.peers[N].port`    | `3868`    | `SIPGATE_DIAMETER_PEERS_0_PORT`        | Port of a Diameter peer |
| `sipgate.diameter.reconnect-delay`  | `PT30S`   | `SIPGATE_DIAMETER_RECONNECT_DELAY`     | Delay between reconnect attempts |
| `sipgate.diameter.watchdog-interval`| `PT30S`   | `SIPGATE_DIAMETER_WATCHDOG_INTERVAL`   | Connection watchdog interval |
| `sipgate.diameter.host-ip`          | local host address | `SIPGATE_DIAMETER_HOST_IP`    | Local address for outbound Diameter connections |
| `sipgate.diameter.capabilities`     | `Cx/Dx`, `S6a/S6d` (code); all three in the shipped `application.yaml` | `SIPGATE_DIAMETER_CAPABILITIES` | Which interfaces to load and advertise — see [Diameter setup](../getting-started/diameter-setup.md) |

## Subscriber data

| Property                       | Default                                  | Environment variable          | Description |
|--------------------------------|------------------------------------------|-------------------------------|-------------|
| `sipgate.profileDir`           | `/usr/local/etc/sparta-hss/imsiProfiles` | `SIPGATE_PROFILEDIR`          | Directory with the S6a/SWx subscription-data XML profiles |
| `sipgate.ims.userProfile.path` | `/usr/local/etc/sparta-hss/user-profile.xml` | `SIPGATE_IMS_USERPROFILE_PATH` | The Cx user-profile template |

Both are loaded at startup — a missing directory or a missing `default` profile
fails the boot. See [XML profiles](xml-profiles.md).

## Authentication

### `sipgate.auc.stored-key-format`

Environment variable: `SIPGATE_AUC_STORED_KEY_FORMAT`.

Whether the `sim` table's `op` column holds the pre-computed **OPc** or the raw
**OP**:

- `opc` (default) — TS 35.205 recommendation; no derivation cost per authentication
- `op` — OPc is derived from (Ki, OP) on every authentication

## HTTP admin endpoints

All three are **opt-in** and off by default.

| Property                             | Default | Environment variable               | Endpoint                        | What it does |
|--------------------------------------|---------|------------------------------------|---------------------------------|--------------|
| `sipgate.http.profile.enabled`       | `false` | `SIPGATE_HTTP_PROFILE_ENABLED`     | `POST /profile/imsi/batch`, `POST /profile/tac/batch` | [Re-profile subscribers in batches, with ISD push](../guides/profile-management.md) |
| `sipgate.http.roaming-block.enabled` | `false` | `SIPGATE_HTTP_ROAMING_BLOCK_ENABLED` | `POST /roaming/block/batch`   | [Manage the roaming rule set](../guides/roaming-management.md) |
| `sipgate.http.volte-location.enabled`| `false` | `SIPGATE_HTTP_VOLTE_LOCATION_ENABLED`| `GET /volte-location/{msisdn}`| [Look up a subscriber's S-CSCF and visited PLMN](http-api.md#volte-location-opt-in) |

The profile endpoints additionally require the `S6a/S6d` capability to be enabled.
See [HTTP API](http-api.md).

## Metrics

| Property                         | Default | Environment variable | Description |
|----------------------------------|---------|----------------------|-------------|
| `sipgate.metrics.esim-imsi-ranges` | empty  | `SIPGATE_METRICS_ESIM_IMSI_RANGES` | The operator's eSIM IMSI ranges — a list of `{from, to}` string pairs, compared inclusively. Subscribers inside any range count as eSIMs, all others as plastic SIMs in the `roaming_location` gauge. |

```yaml
sipgate:
  metrics:
    esim-imsi-ranges:
      - from: "001011000000000"
        to: "001011999999999"
```

Each list element is set as `SIPGATE_METRICS_ESIM_IMSI_RANGES_0_FROM` /
`SIPGATE_METRICS_ESIM_IMSI_RANGES_0_TO` (and `_1_`, `_2_`, ...) via environment
variables.

## Roaming

| Property                                          | Default       | Environment variable                                | Description |
|---------------------------------------------------|---------------|-----------------------------------------------------|-------------|
| `sipgate.roaming.emergency-mcc-allowlist.enabled` | `false`       | `SIPGATE_ROAMING_EMERGENCY_MCC_ALLOWLIST_ENABLED`   | Enable the emergency MCC allowlist (blocks all roaming outside the list) — see [Roaming](../concepts/roaming.md#emergency-mcc-allowlist) |
| `sipgate.roaming.emergency-mcc-allowlist.allowed-mccs` | Zone-One MCCs | `SIPGATE_ROAMING_EMERGENCY_MCC_ALLOWLIST_ALLOWED_MCCS` | The allowed MCCs; empty means the built-in Zone-One set |

## Database

Standard Spring Boot datasource and JPA properties — see the
[Spring Boot application properties reference](https://docs.spring.io/spring-boot/appendix/application-properties/index.html).

| Property                                        | Default                              | Environment variable                    | Description |
|-------------------------------------------------|--------------------------------------|-----------------------------------------|-------------|
| `spring.datasource.url`                         | `jdbc:sqlite:sparta-hss.db`          | `SPRING_DATASOURCE_URL`                 | Zero-setup SQLite default; point at a client/server DB for production |
| `spring.datasource.driver-class-name`           | `org.sqlite.JDBC`                    | `SPRING_DATASOURCE_DRIVER_CLASS_NAME`   | JDBC driver for the datasource |
| `spring.jpa.hibernate.ddl-auto`                 | `update`                             | `SPRING_JPA_HIBERNATE_DDL_AUTO`         | Set `none` when you manage the schema yourself |
| `spring.jpa.hibernate.naming.physical-strategy` | standard (no snake_case)             | `SPRING_JPA_HIBERNATE_NAMING_PHYSICAL_STRATEGY` | Table/column names are taken from the entities as written |
| `spring.jpa.properties.hibernate.dialect`       | `org.hibernate.community.dialect.SQLiteDialect` | `SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT` | Hibernate dialect; must match the datasource |
| `spring.jpa.open-in-view`                       | `false`                              | `SPRING_JPA_OPEN_IN_VIEW`               | No shared persistence context across the HTTP request |

## Actuator

Standard Spring Boot actuator properties — see the
[Spring Boot application properties reference](https://docs.spring.io/spring-boot/appendix/application-properties/index.html).

| Property                                      | Default                 | Environment variable                      | Description |
|-----------------------------------------------|-------------------------|-------------------------------------------|-------------|
| `management.endpoints.web.base-path`          | `/`                     | `MANAGEMENT_ENDPOINTS_WEB_BASE_PATH`      | Endpoints are exposed at the root: `/health`, `/metrics`, `/prometheus` |
| `management.endpoints.web.exposure.include`   | `health`, `metrics`, `prometheus` | `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | The actuator endpoints exposed over HTTP |
| `management.endpoint.health.show-details`     | `always`                | `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | Health answers always carry the per-indicator details |

Health groups: `live` = `ping` (the app is up), `ready` = `db` +
`diameterConnection` (database reachable and at least one Diameter peer connected) —
see [Monitoring](../guides/monitoring.md).
