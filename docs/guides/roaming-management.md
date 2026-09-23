# Roaming management

This guide covers operating the [roaming blocking](../concepts/roaming.md) feature:
setting rules, reading them back, and watching the outcome.

## Enabling the admin endpoint

```yaml
sipgate:
  http:
    roaming-block:
      enabled: true
```

## Setting rules

`POST /roaming/block/batch` **replaces the whole rule set** — always submit the full
set you want, not a delta. The response (`200 OK`) is a dump of everything now
stored, grouped by the storage tables — see the [HTTP API](../reference/http-api.md#post-roamingblockbatch)
for the shape.

Changing the rules does **not** push anything to the network — there is no ISD or
other signalling involved. Affected subscribers pick up the new state on their
**next ULR**, so the effect takes gradually.

### Example

Block all roaming into a maintenance network, except one whitelisted IMSI:

```shell
curl -s -X POST localhost:8080/roaming/block/batch \
  -H 'Content-Type: application/json' \
  -d '{
    "networks": [
      {
        "mcc-mnc": ["001-01"],
        "gt-prefix": ["4917012345"],
        "reason": "maintenance window 2026-09-22"
      }
    ],
    "imsis": {
      "001011000000001": {
        "mcc-mnc": ["001-01"],
        "gt-prefix": [],
        "roaming": "allowed",
        "reason": "field test exception"
      }
    }
  }'
```

Semantics:

- `networks[].mcc-mnc` — PLMN block, applied to every subscriber
- `networks[].gt-prefix` — block by serving-node identity prefix
- `imsis.<IMSI>.roaming` — `blocked`, `allowed`, or `default` (fall through to the
  network rules); IMSIs must be 13–15 digits (`400` otherwise)
- `imsis.<IMSI>.mcc-mnc` / `gt-prefix` — the networks the override applies to

### Clearing rules

Submit an empty rule set to remove everything:

```shell
curl -s -X POST localhost:8080/roaming/block/batch \
  -H 'Content-Type: application/json' \
  -d '{"networks": [], "imsis": {}}'
```

## Emergency allowlist

Use the emergency restriction when you need to cut roaming off to every network
outside a set of allowed countries **at once** — for example during an incident, or
when roaming to untrusted or unsupported networks must be stopped wholesale. It takes
precedence over the rule set and blocks every ULR whose MCC is not allowlisted:

```yaml
sipgate:
  roaming:
    emergency-mcc-allowlist:
      enabled: true
      allowed-mccs: [262]   # optional; defaults to the Zone-One set
```

ULRs from blocked MCCs are answered `5012` (transient) so phones retry rather than
forbidding the PLMN.

## Monitoring

| Metric | What to watch |
|--------|---------------|
| `roaming_blocked_total{mcc=...}` | Steady rises while a block is active; a spike after a rule change means the rule is broader than intended |
| `roaming_blocked_recovered_bucket{le=...}` | Subscribers who came back within the SLO window (1–5 attempts) |
| `roaming_blocked_not_recovered_count` | Subscribers who never came back while the block was on — candidates for follow-up |
| `roaming_blocked_emergency_total{mcc=...}` | ULRs rejected by the emergency allowlist |

Note that blocked subscribers do not accumulate location state: the block happens
**before** anything is stored, so removing the rule is all it takes for them to
attach again.
