# sparta-hss

## Running via Docker

Images are published to [`sipgategmbh/sparta-hss-foss`](https://hub.docker.com/r/sipgategmbh/sparta-hss-foss)
for `linux/amd64` and `linux/arm64`. Releases are tagged `:<version>`, `:latest` follows the most recent
release, and every build of `main` is published as `:<version>-SNAPSHOT`.

The Diameter peer configuration has no usable default. Left unset, the container still starts and serves
`/health/live`, but the placeholders reach the Diameter node verbatim, so it never connects to a peer and
`/health/ready` stays `DOWN`. Set all of these:

| Environment variable              | Maps to                          | Example             |
|-----------------------------------|----------------------------------|---------------------|
| `SIPGATE_DIAMETER_ORIGIN_HOST`    | `sipgate.diameter.origin-host`   | `hss.example.com`   |
| `SIPGATE_DIAMETER_ORIGIN_REALM`   | `sipgate.diameter.origin-realm`  | `example.com`       |
| `SIPGATE_DIAMETER_PEERS_0_HOST`   | `sipgate.diameter.peers[0].host` | `dra.example.com`   |
| `SIPGATE_DIAMETER_PEERS_0_PORT`   | `sipgate.diameter.peers[0].port` | `3868` (the default)|

Any other property from `application.yaml` can be overridden the same way, for example
`SIPGATE_DIAMETER_CAPABILITIES=Cx/Dx,S6a/S6d` to load only those interfaces.

```shell
docker run \
  -e SIPGATE_DIAMETER_ORIGIN_HOST=hss.example.com \
  -e SIPGATE_DIAMETER_ORIGIN_REALM=example.com \
  -e SIPGATE_DIAMETER_PEERS_0_HOST=dra.example.com \
  -p 8080:8080 \
  -v sparta-hss-data:/var/lib/sparta-hss \
  sipgategmbh/sparta-hss-foss:latest
```

The subscriber-facing defaults live in `/usr/local/etc/sparta-hss`, so one mount replaces all of them:

* `user-profile.xml` — the Cx user-profile template (`{privateId}` and `{msisdn}` are substituted per
  request). The shipped one declares no application server.
* `imsiProfiles/` — the S6a subscription-data profiles; `default.xml` is used for any subscriber without
  a more specific [profile assignment](#subscriber-profiles).

Copy `docker/config` from this repository as a starting point and mount it with
`-v ./config:/usr/local/etc/sparta-hss:ro`. A directory mounted there replaces the shipped defaults
entirely, so it must contain both entries.

`/var/lib/sparta-hss` holds the zero-setup SQLite database and is the only writable path: without a volume
there, all S-CSCF bindings and subscriber state are lost with the container. For production traffic point
`SPRING_DATASOURCE_URL` at a client/server database instead. The process runs as uid 10001, so a bind mount
must be writable by that user.

## HTTP interface

The list of available endpoints is:

* `/health` (`GET`)
    * `/health/ready` (`GET`) - [health check](#health-check)
    * `/health/live` (`GET`) - [health check](#health-check)
* `/metrics` (`GET`) - [metrics](#available-metrics) in JSON format. This route is navigatable.
* `/prometheus` (`GET`) - [metrics](#available-metrics) in OpenTelemetry/Prometheus format.
* `/profile/imsi/batch` (`POST`) - send a map of IMSI to profiles to store and trigger InsertSubscriberData to MME.
* `/profile/tac/batch` (`POST`) - send a map of TAC to profiles to store and trigger InsertSubscriberData to MME.


### Available Metrics

Metrics are exposed using Prometheus: http://localhost:8080/prometheus

The following metrics are available using Micrometer:

| Name                                             | Type      | Tags                             | Description                                                                                                         |
|--------------------------------------------------|-----------|----------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `diameter_request_duration_millis`               | Timer     | `request_type`, `app_id`          |                                                                                                                     |
| `diameter_request_result`                        | Timer     | `request_type`, `app_id`, `result_code`, `mnc_origin_realm`, `mcc_origin_realm` |                                                                                                                     |
| `diameter_server_assignment_request_type`        | Counter   | `interface`, `type`              | Type of request inside SAR.                                                                                         |
|                                                  |           |                                  |                                                                                                                     |
| `effective_profile`                              | Counter   | `profile`, `reason`              |                                                                                                                     |
|                                                  |           |                                  |                                                                                                                     |
| `milenage_generate_vector`                       | Counter   | `technology`                     |                                                                                                                     |
| `milenage_generate_aka`                          | Counter   |                                  |                                                                                                                     |
| `milenage_generate_kasme`                        | Counter   | `mcc`, `mnc`                     |                                                                                                                     |
| `milenage_resync_sqn_attempt`                    | Counter   |                                  |                                                                                                                     |
| `milenage_resync_sqn_success`                    | Counter   |                                  |                                                                                                                     |
| `milenage_resync_sqn_invalid_mac`                | Counter   |                                  |                                                                                                                     |
| `milenage_resync_sqn_exception`                  | Counter   | `exception`                      |                                                                                                                     |
|                                                  |           |                                  |                                                                                                                     |
| `non_3gpp_user_data`                             | Counter   | `profile`                        |                                                                                                                     |
|                                                  |           |                                  |                                                                                                                     |
| `roaming_blocked`                                | Counter   | `mcc`, `component`               | Tracks number of times we blocked an IMSI from roaming                                                              |
| `roaming_blocked_recovered`                      | Histogram | `mcc`, `component`               | Tracks number of times an IMSI came back and was allowed for roaming within the expected time (SLO buckets 1-5)     |
| `roaming_blocked_not_recovered`                  | Histogram | `mcc`, `component`               | Tracks number of times an IMSI was blocked but never allowed for roaming within the expected time (SLO buckets 1-5) |
| `roaming_blocked_emergency`                      | Counter   | `mcc`                            | Tracks number of times an IMSI was rejected by the emergency roaming restriction.                                  |
|                                                  |           |                                  |                                                                                                                     |
| `roaming_location`                               | Gauge     | `sim_technology`, `mcc`, `mnc`, `country`, `continent` | Number of SIM cards per visited network, split by eSIM/plastic SIM.                                   |
|                                                  |           |                                  |                                                                                                                     |
| `subscription_data`                              | Counter   | `profile`, `vplmnId`             |                                                                                                                     |



From dependencies, you currently have the following metrics available:

| Name                                             | Type      | Tags                             | Description                                                                                                         |
|--------------------------------------------------|-----------|----------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `executor_active_threads`                        | Gauge     | `name`                           |                                                                                                                     |
| `executor_completed_tasks_total`                 | Counter   | `name`                           |                                                                                                                     |
| `executor_pool_size_threads`                     | Gauge     | `name`                           |                                                                                                                     |
| `executor_queued_tasks`                          | Gauge     | `name`                           |                                                                                                                     |
| `executor_queue_remaining_tasks`                 | Gauge     | `name`                           |                                                                                                                     |
| `executor_pool_core_threads`                     | Gauge     | `name`                           |                                                                                                                     |
| `executor_pool_max_threads`                      | Gauge     | `name`                           |                                                                                                                     |
|                                                  |           |                                  |                                                                                                                     |
| `http_servlet_duration_seconds_bucket`           | Histogram | `method`, `path`                 | Time taken for a HTTP request to be processed.                                                                      |
| `http_servlet_duration_seconds_count`            | Counter   | `method`, `path`                 |                                                                                                                     |
| `http_servlet_duration_seconds_sum`              | Counter   | `method`, `path`                 |                                                                                                                     |
| `http_servlet_duration_seconds_max`              | Gauge     | `method`, `path`                 | (Reset after default of 1 minute)                                                                                   |

## Subscriber Profiles

In the Update Location Answer, each subscriber gets their own profile. There is a default profile, but you can define
a special profile per IMSI or per TAC.

The assignments are stored in the tables `imsi_profile` and `tac_profile`. The effective profile is determined by the
following rules, for example:

**imsi_profile**

| imsi | profile  | effective profile |
|------|----------|-------------------|
| 1    | (null)   | default           |
| 2    | volte    | volte             |
| 3    | no-volte | no-volte          |

**tac_profile**

| tac | profile  | effective profile |
|-----|----------|-------------------|
| a   | (null)   | default           |
| b   | volte    | volte             |
| c   | no-volte | no-volte          |

**effective profile based on UpdateLocationRequest (ULR)**

TL;DR: The value in `imsi_profile` takes precedence over `tac_profile`.


## Local development

### Run via IDE

To run your service locally, create or use the existing Run Configuration for `SpartaHssApplication`.


### Implement new Diameter commands/messages

Diameter is implemented natively via the `sparta-diameter` client library
(`com.sipgate:sparta-diameter-3gpp-s6a` / `com.sipgate:sparta-diameter-3gpp-cxdx`). The HSS connects as a
Diameter peer to the configured DRA(s) (`sipgate.diameter.peers`) and registers one handler per supported
request type in `DiameterConnectionHandler`. The handlers live in `com.sipgate.sparta.hss.diameter`,
grouped by application (`s6a`, `cx`) and command.

1. If the command's message model does not exist yet, add it to the `sparta-diameter` library.
2. Create a handler in the matching `com.sipgate.sparta.hss.diameter` package.
3. Register the handler in `DiameterConnectionHandler`.
4. Write tests against the handler (see the existing handler tests for the encode/decode test setup).


## Releasing

New versions are cut by maintainers from a GitHub Actions workflow — see [RELEASING.md](RELEASING.md).


