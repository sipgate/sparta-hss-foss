# HTTP API

All endpoints are served on port `8080` at the root path. The three admin endpoints
are opt-in (off by default) — see [Configuration](configuration.md#http-admin-endpoints).

## Health

### `GET /health/live`

Liveness. `200` as soon as the application is up.

### `GET /health/ready`

Readiness. `200` when the database is reachable **and** at least one Diameter peer
connection is established. The answer's `details` carry the state of every peer:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diameterConnection": {
      "status": "UP",
      "details": {
        "peerStates": { "DiameterPeer[host=dra.example.com, port=3868]": "I_OPEN" }
      }
    }
  }
}
```

## Metrics

### `GET /prometheus`

All metrics in Prometheus/OpenMetrics exposition format. See
[Metrics](metrics.md) for the catalog.

## Subscriber profiles (opt-in, S6a/S6d)

### `POST /profile/imsi/batch`

Assign profiles to a batch of IMSIs. The body is a map of IMSI to profile name:

```json
{
  "001011000000001": "volte",
  "001011000000002": "no-volte"
}
```

Returns `204 No Content` immediately; the assignments are stored and each affected
subscriber's MME is notified with an Insert Subscriber Data request, throttled to one
per 500 ms. See [Profile management](../guides/profile-management.md).

### `POST /profile/tac/batch`

Same shape for TACs: a map of TAC to profile name.

```json
{
  "01124500": "no-volte"
}
```

## Roaming rules (opt-in)

### `POST /roaming/block/batch`

**Replaces the entire rule set** with the submitted one, then answers with a dump of
all stored rules. A `400` is returned when the body is invalid (e.g. an IMSI with more
than 15 or fewer than 13 digits).

```json
{
  "networks": [
    {
      "mcc-mnc": ["001-01"],
      "gt-prefix": ["4917012345"],
      "reason": "network under maintenance"
    }
  ],
  "imsis": {
    "001011000000001": {
      "mcc-mnc": ["001-01"],
      "gt-prefix": [],
      "roaming": "blocked",
      "reason": "troubleshooting"
    }
  }
}
```

Field semantics:

- `networks` — block rules that apply to **every** subscriber:
  - `mcc-mnc` — list of `MCC-MNC` strings
  - `gt-prefix` — list of serving-node identity prefixes
  - `reason` — free text, stored with the rule
- `imsis` — per-IMSI overrides, keyed by IMSI:
  - `mcc-mnc`, `gt-prefix` — as above
  - `roaming` — `allowed`, `blocked`, or `default` (fall through to the network rules)
  - `reason` — free text

The response is `200 OK` with the full stored rule set. The request is expanded
into one row per subscriber and network combination, and the answer dumps those
rows keyed by the four storage tables — it is **not** a mirror of the request
shape:

```json
{
  "imsiOverrides": [],
  "imsiOverridesLte": [
    { "id": 1, "imsi": "001011000000001", "mcc": "001", "mnc": "01", "roaming": "blocked", "reason": "troubleshooting" }
  ],
  "blockedLocations": [
    { "id": 1, "gtPrefix": "4917012345", "reason": "network under maintenance" }
  ],
  "blockedLocationsLte": [
    { "id": 1, "mcc": "001", "mnc": "01", "reason": "network under maintenance" }
  ]
}
```

- `blockedLocations` / `blockedLocationsLte` — one entry per `gt-prefix` /
  `mcc-mnc` value from the `networks` rules
- `imsiOverrides` / `imsiOverridesLte` — one entry per IMSI and network pair,
  including the `roaming` state

## VoLTE location (opt-in)

### `GET /volte-location/{msisdn}`

The current VoLTE-relevant location of a subscriber:

- `200 OK`

  ```json
  {
    "location": "scscf.example.com",
    "mcc": "262",
    "mnc": "01"
  }
  ```

  `location` is the serving S-CSCF; `mcc`/`mnc` are the visited PLMN (defaults to
  `262`/`22` when the subscriber has no stored visited PLMN).
- `404 Not Found` — the MSISDN has no S-CSCF assignment **updated within the last 24
  hours**. An older assignment is treated as stale and answers `404` as well.
