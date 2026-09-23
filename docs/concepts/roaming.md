# Roaming

sparta-hss can block roaming on a per-network or per-subscriber basis, and applies an
emergency allowlist of MCCs. All of this is checked on every Update Location Request.

## Rule model

Two layers, stored in the `roaming_blocked_*` tables:

**Network rules** — block every subscriber on a given network. Two rule types:
`mcc-mnc` (e.g. `001-01`) and `gt-prefix` (matching a global-title prefix of the
serving node). The GT-prefix rules exist because at sipgate the 2G core shares
the same database: blocking roaming on 2G needs the GT, not the PLMN. A 2G core
sharing the database can make use of the same mechanism — sipgate has not published
its own mobile core.

**IMSI overrides** — per-IMSI decisions on a given network, each one `allowed`,
`blocked`, or `default` (fall through to the network rule).

The HSS itself evaluates **only the MCC/MNC rules**: the GT-prefix rules are stored
and returned by the API, but no code path in the HSS reads them.

Evaluation order for a ULR from visited network (MCC, MNC):

```
IMSI override for (imsi, mcc, mnc)?
├── allowed → roaming allowed
├── blocked → roaming blocked
└── default / absent
    └── network rule for (mcc, mnc)?
        ├── blocked → roaming blocked
        └── absent  → roaming allowed
```

## Emergency MCC allowlist

A coarse switch that blocks **all** roaming outside an allowlist of MCCs — meant for
disaster scenarios. Enabled via
[`sipgate.roaming.emergency-mcc-allowlist`](../reference/configuration.md#roaming);
with no explicit list it defaults to the GSM "Zone One" countries where EU roaming
applies.

A ULR from a non-allowlisted MCC is answered with `DIAMETER_UNABLE_TO_COMPLY` (5012)
rather than the roaming-not-allowed error — 5012 is a transient signal that makes
phones retry without forbidding the PLMN. The UE must not store the PLMN as
forbidden: that can ban the phone from all networks permanently, and it will never
retry once the emergency roaming ban is lifted.

## Blocked events and recovery

These metrics are meant to **detect roaming issues**: if an IMSI does not come back
on another PLMN shortly after a `ROAMING_NOT_ALLOWED`, that indicates a roaming
issue that you may want to investigate.

Every blocked ULR records a **blocked event** (IMSI + MCC). Two outcomes close an
event:

- **Recovered** — the same IMSI is later *allowed* on **any PLMN**. Recovery is
  IMSI-wide: one allowed ULR clears that subscriber's pending events for every MCC,
  and the count is recorded in the `roaming_blocked_recovered` histogram tagged with
  the MCC the subscriber came back on.
- **Not recovered** — the event expires (checked every 5 minutes) without an allow;
  it is recorded in `roaming_blocked_not_recovered`.

Both histograms use SLO buckets of 1–5 attempts, so "how many roaming attempts did it
take until the subscriber got back in" is directly queryable. See
[Roaming metrics](../reference/metrics.md#roaming).

## Management

Rules are managed through the (opt-in) `POST /roaming/block/batch` endpoint — see
[Roaming management](../guides/roaming-management.md).
