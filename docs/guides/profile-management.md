# Profile management

The profile system exists to flip subscription features (typically VoLTE) on or off
**per TAC** (e.g. a broken TAC) or **per IMSI** (e.g. a beta tester or an unhappy
customer) without touching the subscriber's base profile.

## Setup

1. Put the profiles in `sipgate.profileDir` — e.g. `volte.xml` and `no-volte.xml`
   next to the required `default.xml`
   ([format](../reference/xml-profiles.md#subscription-data-profiles)).
2. Enable the admin endpoints — in `application.yaml`:

   ```yaml
   sipgate:
     http:
       profile:
         enabled: true
   ```

   or, for a container deployment, with the environment variable
   `SIPGATE_HTTP_PROFILE_ENABLED=true`.

3. (Optional) Add PLMN-specific overrides for visited networks:
   `imsiProfiles/214/no-volte.xml` applies `no-volte` to subscribers roaming into MCC
   214.

Profile files are read **at startup** — after adding, changing or removing a profile
file, restart the HSS. An unknown profile name in an assignment fails the request
that needs it (and a missing `default.xml` fails the boot).

## Applying profiles

### Single subscriber / TAC

Write directly to the database:

```sql
-- block VoLTE for one IMSI
INSERT INTO imsi_profile (imsiId, name)
SELECT id, 'no-volte' FROM imsi WHERE imsi = '001011000000001';

-- block VoLTE for a TAC (device model)
INSERT INTO tac_profile (tac, name) VALUES ('01124500', 'no-volte');
```

A plain insert changes the profile for **future** ULRs; existing MMEs keep their old
subscription data until the next registration or until you push it.

### Batches (with ISD push)

The batch endpoints store the assignments **and** push Insert Subscriber Data to the
affected MMEs, so connected subscribers pick up the change immediately:

```shell
curl -s -X POST localhost:8080/profile/imsi/batch \
  -H 'Content-Type: application/json' \
  -d '{"001011000000001": "no-volte", "001011000000002": "volte"}'

curl -s -X POST localhost:8080/profile/tac/batch \
  -H 'Content-Type: application/json' \
  -d '{"01124500": "no-volte"}'
```

Both return `204 No Content` immediately and process in the background.

### Planning for scale

The ISD push is throttled to **one ISD per 500 ms** over the whole batch, and one
ISD goes to every affected subscriber — changing a single TAC can affect all
subscribers whose effective profile comes from that TAC. The number of ISDs is the
number of affected subscribers, not the number of keys in the request.

| Affected subscribers | Approx. push time |
|----------------------|-------------------|
| 1,000                | ~8 min            |
| 10,000               | ~83 min           |
| 20,000               | ~167 min          |

Progress shows up in the logs (`Throttling active, still to go: N`). The DB write
itself is fast; the long tail is the throttled push.

## Observing the effect

- `effective_profile_total{profile=..., reason=...}` — which profile each ULR resolved
  to, and why (IMSI / TAC / DEFAULT)
- `subscription_data_total{profile=..., vplmnId=...}` — subscription-data answers per
  profile and visited PLMN
- `milenage_generate_vector_total{technology="4g"}` — a TAC that should have lost VoLTE
  but still authenticates over E-UTRAN shows up here

## Reverting

Revert through the batch endpoints whenever the MMEs should be updated immediately:
they push the ISD for every affected subscriber, like any other change.

Assigning a profile back to `default` does **not** remove the row — it stores the
name `default`, which resolves to the default profile. To remove an assignment
entirely (or to avoid the ISD push), delete the row directly:

```sql
DELETE FROM imsi_profile WHERE imsiId = (SELECT id FROM imsi WHERE imsi = '001011000000001');
```
