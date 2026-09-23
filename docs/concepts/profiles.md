# Subscriber profiles

Every Update Location Answer (and every SWx registration answer) carries the
subscriber's subscription data, taken from a **profile**: an XML file with the
subscription-data content (see [XML profiles](../reference/xml-profiles.md) for the
format).

## Where profiles come from

Profiles are files in `sipgate.profileDir` (Docker: `/usr/local/etc/sparta-hss/imsiProfiles`):

- `default.xml` is **required** and used for any subscriber without a more specific
  assignment.
- Any other file defines a named profile (`volte.xml` → profile `volte`).
- Subdirectories provide PLMN-specific overrides, checked in this order:
  1. `<vplmnId>/<profile>.xml` — the full visited PLMN (MCC+MNC)
  2. `<mcc>/<profile>.xml` — the visited MCC
  3. `<profile>.xml` — the base profile

All files are loaded at startup; a missing directory or a missing `default.xml` fails
the boot. Changing profiles requires a **restart** of the HSS — there is no
hot-reloading.

## Assignment

Assignments are stored in the database:

- `imsi_profile` — one profile per IMSI
- `tac_profile` — one profile per TAC

A TAC is a **Type Allocation Code**: it identifies the *device model*, not the
subscriber or the location. This makes per-device-model profiles possible — for
example disabling VoLTE for device models that the IMS core cannot serve, by giving
them a profile without the `ims` APN subscription. Only the TAC is stored in the
HSS, never the full IMEI: the IMEI itself is not of interest (privacy), but the TAC
is useful for handling special cases on the network.

## Effective profile

For a ULR, the effective profile is determined by **IMSI precedence over TAC**:

| ULR.imsi | ULR.tac | imsi_profile | tac_profile | effective profile | reason | Explanation                                                    |
|----------|---------|--------------|-------------|-------------------|--------|----------------------------------------------------------------|
| 1        | a       | (null)       | (null)      | default           | DEFAULT| Whatever default means: VoLTE or no VoLTE                      |
| 1        | b       | (null)       | volte       | volte             | TAC    | VoLTE allowed for the device models reporting TAC "b"          |
| 1        | c       | (null)       | no-volte    | no-volte          | TAC    | VoLTE disabled for unsupported device models on TAC "c"        |
| 2        | a       | volte        | (null)      | volte             | IMSI   | VoLTE enabled for IMSI "2" — troubleshooting for a specific customer |
| 2        | b       | volte        | volte       | volte             | IMSI   |                                                                  |
| 2        | c       | volte        | no-volte    | volte             | IMSI   | IMSI "2" keeps VoLTE even on a TAC where it is disabled         |
| 3        | a       | no-volte     | (null)      | no-volte          | IMSI   | VoLTE disabled for IMSI "3" — troubleshooting for a specific customer |
| 3        | b       | no-volte     | volte       | no-volte          | IMSI   | IMSI "3" stays disabled even on a TAC where VoLTE is allowed    |
| 3        | c       | no-volte     | no-volte    | no-volte          | IMSI   |                                                                  |

The selection is counted per profile and reason in the
[`effective_profile`](../reference/metrics.md) metric.

## Notes

- The TAC used for the TAC lookup is derived from the IMEI in the request (first 8
  digits) — only present on S6a (MME) requests, not S6d (SGSN).
- The MSISDN element in a profile file is ignored: the MSISDN AVP in the answer is
  always taken from the subscriber database.
- Profile changes made through the [batch endpoints](../guides/profile-management.md)
  trigger Insert Subscriber Data requests to the affected MMEs.
