# Metrics

Metrics are exposed via Micrometer on
[`/prometheus`](http-api.md#metrics), in Prometheus/OpenMetrics exposition format.

## Diameter

| Name | Type | Tags | Description |
|------|------|------|-------------|
| `diameter_request_duration_millis` | Timer | `request_type`, `app_id` | Processing time of inbound Diameter requests |
| `diameter_request_result` | Counter | `request_type`, `app_id`, `result_code`, `mnc_origin_realm`, `mcc_origin_realm` | Inbound requests per result code, per peer realm |
| `diameter_server_assignment_request_type` | Counter | `interface` (`cx`, `swx`), `type` | SAR requests by Server-Assignment-Type (register, reregister, user-deregistration, ...) |

`request_type` carries the full command name, not the abbreviation —
`Authentication-Information Request`, `Update-Location Request`,
`Server-Assignment Request`, `Multimedia-Auth Request`, `Purge-UE Request`,
`Notify Request`. `result_code` is a string, not the numeric code:
`diameter_success`, `experimental_<code>` (e.g. `experimental_5420`) for a 3GPP
Experimental-Result, and `5012_unable_to_comply` for an unexpected handler failure.
A peer realm that does not parse as an MCC/MNC yields `unknown` for both realm tags.

## Diameter connections (from the client library)

| Name | Type | Tags | Description |
|------|------|------|-------------|
| `diameter_connections_active` | Gauge | — | Peer connections currently established |
| `diameter_connections_active_direction` | Gauge | `direction` (`inbound`, `outbound`) | The same, split by direction |
| `diameter_connections` | Counter | `direction` | Connections established |
| `diameter_disconnections` | Counter | `direction` | Connections lost |
| `diameter_connect_error` | Counter | `cause` (exception class) | Failed connection attempts |
| `diameter_commands_received` | Counter | `application_id`, `command_code`, `command_type` (`request`, `answer`) | Messages received |
| `diameter_commands_sent` | Counter | `application_id`, `command_code`, `command_type` | Messages sent |
| `diameter_handler_duration` | Timer | `application_id`, `command_code` | Handler execution time, per command code |
| `diameter_handler_errors` | Counter | `application_id`, `command_code`, `cause` (exception class) | Handler failures; a business error is a `DiameterErrorAnswerException` |
| `diameter_request_duration` | Timer | `application_id`, `command_code` | Round-trip time of requests the **HSS sends** (CER, CLR, ISD, RTR) |

`diameter_connections_active` is the metric to alert on for peer loss — it does not
depend on traffic flowing. Note that `diameter_request_duration` (outbound, from the
library) and `diameter_request_duration_millis` (inbound, from the HSS) are different
metrics despite the similar names.

## Authentication

| Name | Type | Tags | Description |
|------|------|------|-------------|
| `milenage_generate_vector` | Counter | `technology` (`4g`, `3g`) | Authentication vectors generated per RAT |
| `milenage_generate_aka` | Counter | — | Total AKA generations |
| `milenage_generate_kasme` | Counter | `mcc`, `mnc` | KASME derivations per visited PLMN (`(null)` when no VPLMN) |
| `milenage_resync_sqn_attempt` | Counter | — | SQN re-synchronisation attempts |
| `milenage_resync_sqn_success` | Counter | — | Successful re-synchronisations |
| `milenage_resync_sqn_invalid_mac` | Counter | — | Re-synchronisations rejected by a MAC-S mismatch |
| `milenage_resync_sqn_exception` | Counter | `exception` | Re-synchronisation failures by exception type |

## Profiles and subscription data

| Name | Type | Tags | Description |
|------|------|------|-------------|
| `effective_profile` | Counter | `profile`, `reason` (`IMSI`, `TAC`, `DEFAULT`) | Effective-profile selections per ULR — see [Subscriber profiles](../concepts/profiles.md) |
| `subscription_data` | Counter | `profile`, `vplmnId` | Subscription-Data answers per profile and visited PLMN |
| `non_3gpp_user_data` | Counter | `profile` | SWx registration answers per profile |

## Roaming

| Name | Type | Tags | Description |
|------|------|------|-------------|
| `roaming_blocked` | Counter | `mcc`, `component` (`hss`) | Blocked ULRs per visited MCC |
| `roaming_blocked_recovered` | Histogram | `mcc`, `component` | Blocked attempts that were **cleared by a subsequent allow**, SLO buckets 1–5 |
| `roaming_blocked_not_recovered` | Histogram | `mcc`, `component` | Blocked attempts that **expired without an allow** (5-minute expiry), SLO buckets 1–5 |
| `roaming_blocked_emergency` | Counter | `mcc` | ULRs rejected by the [emergency MCC allowlist](../concepts/roaming.md#emergency-mcc-allowlist) |
| `roaming_location` | Gauge | `sim_technology` (`esim`, `plastic`), `mcc`, `mnc`, `country`, `continent` | Subscribers per visited network, refreshed every minute; the eSIM/plastic split follows the configured [eSIM IMSI ranges](configuration.md#metrics) |

## Infrastructure (from dependencies)

| Name | Type | Tags | Description |
|------|------|------|-------------|
| `executor_active_threads` | Gauge | `name` | Active threads of the Diameter inbound/outbound pools |
| `executor_pool_size_threads` | Gauge | `name` | Current pool size |
| `executor_pool_core_threads` | Gauge | `name` | Core pool size |
| `executor_pool_max_threads` | Gauge | `name` | Max pool size |
| `executor_queued_tasks` | Gauge | `name` | Queued tasks |
| `executor_queue_remaining_tasks` | Gauge | `name` | Remaining queue capacity |
| `executor_completed_tasks_total` | Counter | `name` | Completed tasks |
| `http_servlet_duration_seconds_bucket` | Histogram | `method`, `path` | HTTP request processing time |
| `http_servlet_duration_seconds_count` | Counter | `method`, `path` | HTTP request count |
| `http_servlet_duration_seconds_sum` | Counter | `method`, `path` | HTTP request time, total |
| `http_servlet_duration_seconds_max` | Gauge | `method`, `path` | HTTP request time, max (reset after ~1 minute) |
