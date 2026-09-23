# Deployment

## Container layout

| Path                     | Purpose                                                                  |
|--------------------------|--------------------------------------------------------------------------|
| `/usr/local/etc/sparta-hss`  | Subscriber-facing configuration (read-only)          |
| `/var/lib/sparta-hss`        | The zero-setup SQLite database; the only writable path |
| Port `8080`                | HTTP interface (health, metrics, opt-in admin endpoints)           |

The process runs as uid `10001`. A bind mount of `/var/lib/sparta-hss` must be writable
by that user.

The HSS only **initiates** Diameter connections to its configured peers — there is no
inbound Diameter port to open.

## Configuration

The subscriber-facing defaults live in `/usr/local/etc/sparta-hss`:

- `user-profile.xml` — the Cx user-profile template (`{privateId}` and `{msisdn}` are
  substituted per request). The shipped one declares no application server.
- `imsiProfiles/` — the S6a subscription-data profiles; `default.xml` is used for any
  subscriber without a more specific [profile assignment](../concepts/profiles.md).

Copy `docker/config` from this repository as a starting point and mount it with
`-v ./config:/usr/local/etc/sparta-hss:ro`. A directory mounted there **replaces** the
shipped defaults entirely, so it must contain both entries.

```shell
docker run \
  -e SIPGATE_DIAMETER_ORIGIN_HOST=hss.example.com \
  -e SIPGATE_DIAMETER_ORIGIN_REALM=example.com \
  -e SIPGATE_DIAMETER_PEERS_0_HOST=dra.example.com \
  -p 8080:8080 \
  -v ./config:/usr/local/etc/sparta-hss:ro \
  -v sparta-hss-data:/var/lib/sparta-hss \
  sipgategmbh/sparta-hss-foss:latest
```

## Database

### Zero-setup default (SQLite)

By default the HSS stores its state in a SQLite file at `/var/lib/sparta-hss/sparta-hss.db`
(`SPRING_DATASOURCE_URL=jdbc:sqlite:/var/lib/sparta-hss/sparta-hss.db` inside the image)
with the schema derived from the entities (`ddl-auto: update`). This is good for labs
and trying things out.

Without a volume at `/var/lib/sparta-hss`, all S-CSCF assignments and subscriber state
are lost with the container.

### Production (client/server database)

For production traffic point `SPRING_DATASOURCE_URL` at a client/server database — any
Hibernate-supported dialect works (MySQL, PostgreSQL, ...), with the matching
`SPRING_DATASOURCE_DRIVER_CLASS_NAME` and
`SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT`. Manage the schema yourself (a reference DDL
is shipped in [`db/`](https://github.com/sipgate/sparta-hss-foss/tree/main/db)) and set
`SPRING_JPA_HIBERNATE_DDL_AUTO=none`.

## Multiple instances

- The HSS holds no state in memory beyond the profile files loaded at startup; all
  subscriber state is in the database, which must be shared by all instances.
- Concurrency on a subscriber's key material is serialized with a database row lock
  (the SIM row is locked while a vector is generated), so instances do not corrupt
  the SQN under load.
- The subscriber-facing configuration (the Cx user-profile template and the S6a
  profile files) is loaded at startup and must be **kept in sync between
  instances**: after changing profile files you have to restart the HSS — there is
  no live-reloading.
