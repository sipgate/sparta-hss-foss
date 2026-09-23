# Monitoring

## Health

Use `/health/ready` for load-balancer and orchestrator checks. It is `DOWN` when the
database is unreachable **or** no Diameter peer is connected — the two states you
most want to page on. The `details` of the answer show the per-peer connection state,
which makes a partially connected HSS (e.g. during a DRA maintenance window) visible
without scraping:

```shell
curl -s localhost:8080/health/ready | jq .components.diameterConnection.details
```

### Ready state

`/health/ready` is `UP` when the database is reachable **and** at least one Diameter
peer connection is established (`I_OPEN`). The answer's details carry the state of
every peer — see the [HTTP API](../reference/http-api.md#health) for the format.

## Prometheus

Scrape `/prometheus`:

```yaml
scrape_configs:
  - job_name: sparta-hss
    static_configs:
      - targets: ["hss.example.com:8080"]
        labels:
          instance: sparta-hss
    metrics_path: /prometheus
```

## Suggested alerts

| Alert | Expression | Rationale |
|-------|------------|-----------|
| Not ready | `up == 0` on the `/health/ready` probe | DB or all Diameter peers lost |
| No peer connected | `diameter_connections_active == 0` | Every peer connection is gone; does not depend on traffic |
| No traffic | `rate(diameter_request_duration_millis_seconds_count[5m]) == 0` while `/health/live` is up | Nothing reaching the HSS |
| SQN desync | `rate(milenage_resync_sqn_invalid_mac_total[15m]) > 0` | Repeated re-sync failures indicate a stale/compromised SQN or key issue |
| Roaming block active | `rate(roaming_blocked_total[5m]) > 0` | Informational while a maintenance block is running |
| Blocked subscribers not recovering | `rate(roaming_blocked_not_recovered_count[1h]) > 0` | Subscribers stuck out — check the rule set |
| E-UTRAN access denials | `diameter_request_result_total{request_type="Authentication-Information Request", result_code="experimental_5420"} > 0` | If the `EutranAccessPolicy` extension is in use (`UNKNOWN_EPS_SUBSCRIPTION`) |
| Profile push running | `rate(effective_profile_total[5m])` drops to ~0 during a batch run, then spikes | Batch ISD pushes are long-running; see [Profile management](profile-management.md#planning-for-scale) |
| Outbound queue backlog | `executor_queued_tasks{name=~".*(outbound|inbound).*"} > 50` | The Diameter thread pools (10–40 threads, queue 100) are saturating |

## What the HSS does not tell you

The HSS has no view of the radio side (attach rates, handovers). Pair it with the MME
and core-side metrics for a full picture — the `milenage_*` and
`diameter_request_*` families here are the HSS-side complement.
