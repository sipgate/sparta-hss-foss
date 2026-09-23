# Diameter interfaces

Behaviour reference for the commands the HSS serves. Enabled interfaces are a
deployment decision — see [Diameter setup](../getting-started/diameter-setup.md).
Outbound commands (sent by the HSS) are marked **(out)**.

## Cx/Dx (TS 29.228)

Peer: S-CSCF (via DRA).

### SAR — Server Assignment Request

Dispatched per `Server-Assignment-Type`, which is counted in
`diameter_server_assignment_request_type{interface="cx"}`. Every type below except
`UNREGISTERED_USER` first validates the identity: a missing `Server-Name` fails with
`DIAMETER_MISSING_AVP`, more than one `Public-Identity` with
`DIAMETER_AVP_OCCURS_TOO_MANY_TIMES`, an unknown IMSI or Public-Identity with
`ERROR_USER_UNKNOWN`, and a Public-Identity belonging to another subscriber with
`IDENTITIES_DONT_MATCH`.

| Type | Behaviour | Answer |
|------|-----------|--------|
| `REGISTRATION`, `RE_REGISTRATION` | Store the S-CSCF assignment and the IP-SM-GW/Serving-Node; publish `ScscfAssignmentChanged` | 2001 with the per-subscriber user profile (from the [user-profile template](xml-profiles.md#cx-user-profile-template)), omitted when the request sets `User-Data-Already-Available` |
| `USER_DEREGISTRATION`, `ADMINISTRATIVE_DEREGISTRATION`, `TIMEOUT_DEREGISTRATION`, `DEREGISTRATION_TOO_MUCH_DATA` | Clear the S-CSCF assignment and the IP-SM-GW — but only when the stored S-CSCF equals the request's `Server-Name`; publish `ScscfAssignmentChanged` | 2001 |
| `USER_DEREGISTRATION_STORE_SERVER_NAME`, `TIMEOUT_DEREGISTRATION_STORE_SERVER_NAME` | The same clearing behaviour; only the answer differs | experimental success `SERVER_NAME_NOT_STORED` |
| `AUTHENTICATION_FAILURE`, `AUTHENTICATION_TIMEOUT` | No state change | 2001 |
| `UNREGISTERED_USER` | Unsupported | experimental error `FEATURE_UNSUPPORTED` |
| anything else | — | `DIAMETER_UNABLE_TO_COMPLY` |

### MAR — Multimedia-Auth-Request

IMS authentication (AKAv1-MD5):

- Only `Digest-AKAv1-MD5` is accepted; other schemes get
  `AUTH_SCHEME_NOT_SUPPORTED`.
- A 3G AKA vector is generated (AMF `0x0000`); the answer carries CK, IK, XRES and
  `SIP-Authenticate = RAND‖AUTN`.
- The SIM row is locked for the duration of vector generation (SQN
  read-increment-write) so concurrent MARs for one IMSI cannot overlap SQNs.
- If the subscriber is already registered on a **different** S-CSCF, the old one
  receives an RTR (reason `NEW_SERVER_ASSIGNED`) before the answer is sent **(out)**.

### RTR — Registration Termination Request **(out)**

Fire-and-forget de-registration notice to the S-CSCF previously assigned to a
subscriber. It is sent from the **MAR path only** — when a MAR arrives from a
different S-CSCF than the stored one (reason `NEW_SERVER_ASSIGNED`). A SAR
de-registration clears the assignment without sending an RTR.

## S6a/S6d (TS 29.272)

Peer: MME (S6a) / SGSN (S6d), via DRA.

### AIR — Authentication Information Request

- Up to **one vector per requested RAT** (requested count is capped).
- A **Visited-PLMN-Id is required**: without it (or with fewer than three digits) the
  request is rejected with `UNKNOWN_EPS_SUBSCRIPTION`, before any vector is built and
  regardless of the RATs requested.
- E-UTRAN vectors: RAND, XRES, AUTN (AMF `0x8000`), KASME (derived from the
  Visited-PLMN-Id).
- UTRAN/GERAN vectors: RAND, XRES, AUTN (AMF `0x0000`), CK, IK.
- 4G access is gated by the `EutranAccessPolicy`
  ([extension point](../development/extending.md#eutranaccesspolicy)); a denied
  subscriber gets the `UNKNOWN_EPS_SUBSCRIPTION` error.
- Re-synchronisation for **both** E-UTRAN and UTRAN/GERAN in one request is rejected.
- Errors: unknown IMSI → `ERROR_USER_UNKNOWN`; vector-generation failure →
  `AUTHENTICATION_DATA_UNAVAILABLE`.

### ULR — Update Location Request

The main location/subscription command:

1. **Emergency MCC allowlist** (disabled by default) — a ULR from a non-allowlisted
   MCC is answered `DIAMETER_UNABLE_TO_COMPLY` (5012, a transient signal so UEs
   retry) and counted in `roaming_blocked_emergency`.
2. **Roaming rules** — blocked subscribers get `ROAMING_NOT_ALLOWED` (5004) and a
   [blocked event](../concepts/roaming.md#blocked-events-and-recovery) is recorded;
   allowed subscribers clear their pending blocked events.
3. The MSISDN is resolved (unknown subscriber → `ERROR_USER_UNKNOWN`).
4. For S6a requests (ULR-Flags bit 1 set) the MME location is stored (host, realm,
   visited PLMN, TAC from the IMEI); if the MME **changed**, a CLR is sent to the old
   MME **(out)**.
5. The effective profile is determined ([IMSI over TAC over default](../concepts/profiles.md))
   and the answer carries the [subscription data](xml-profiles.md#s6a-format) for the
   visited PLMN (`effective_profile` and `subscription_data` metrics).
6. `UeVisited` (TAC present) and `LocationUpdated` domain events are published.

### PUR — Purge UE Request

Succeeds for a known IMSI. The stored MME location is only removed when the request
comes **from the currently serving MME** — a PUR from any other origin is a no-op for
the location (TS 29.272).

### NOR — Notify Request

Succeeds for a known IMSI (`ERROR_USER_UNKNOWN` otherwise); no state change.

### CLR — Cancel Location Request **(out)**

Fire-and-forget; tells the previous MME to drop the registration after a UE moved.

### ISD — Insert Subscriber Data Request **(out)**

Fire-and-forget push of new subscription data to the serving MME, sent when profiles
change (e.g. through the [batch endpoints](../guides/profile-management.md)). The
push is throttled to **one ISD per 500 ms** over the whole batch — with a large
number of affected subscribers, expect a long-running push.

## Common error codes

The result codes sparta-hss answers with most often:

| Code | Meaning                                  | Where used |
|------|------------------------------------------|------------|
| 5001 | `ERROR_USER_UNKNOWN`                     | Any command for an unknown IMSI (AIR, ULR, PUR, NOR, Cx/SWx SAR, Cx/SWx MAR) |
| 5002 | `IDENTITIES_DONT_MATCH`                  | Cx SAR where the Public-Identity is not associated with the IMSI |
| 5004 | `ROAMING_NOT_ALLOWED`                    | ULR blocked by the roaming rules |
| 5005 | `IDENTITY_ALREADY_REGISTERED`            | SWx MAR while another AAA server is assigned and no `AAA-Failure-Indication`; SWx SAR on any AAA-server mismatch (register or de-register) |
| 5006 | `AUTH_SCHEME_NOT_SUPPORTED`              | Cx/SWx MAR with an unsupported scheme |
| 5011 | `FEATURE_UNSUPPORTED`                    | Cx SAR `UNREGISTERED_USER` |
| 5012 | `SERVING_NODE_FEATURE_UNSUPPORTED`       | Cx/SWx MAR, internal failure during vector generation |
| 4181 | `AUTHENTICATION_DATA_UNAVAILABLE`        | AIR, vector generation failed |
| 5420 | `UNKNOWN_EPS_SUBSCRIPTION`               | AIR, 4G access denied by the `EutranAccessPolicy` |

In addition, standard Diameter error codes are used where noted
(`DIAMETER_UNABLE_TO_COMPLY`, `DIAMETER_MISSING_AVP`, `DIAMETER_INVALID_AVP_VALUE`).

## SWx (TS 29.273)

Peer: 3GPP AAA Server (VoWiFi), via DRA. The HSS is the SWx server.

### SAR — Server Assignment Request

The requesting AAA server is identified by the request's `Origin-Host`
(`3GPP-AAA-Server-Name` is answer-only). An unknown IMSI fails with
`ERROR_USER_UNKNOWN`; a missing `Origin-Host` or `Server-Assignment-Type` fails with
`DIAMETER_MISSING_AVP`.

| Type | Behaviour | Answer |
|------|-----------|--------|
| `REGISTRATION`, `RE_REGISTRATION` | Store the AAA-server assignment when none is present and publish `AaaServerAssignmentChanged`; a re-registration from the same server is idempotent. The answer's profile is the per-IMSI assignment, or `default` | 2001 with `Non-3GPP-User-Data` from the [subscription-data profiles](xml-profiles.md#subscription-data-profiles) |
| `REGISTRATION`, `RE_REGISTRATION` from a **different** AAA server | No state change | `IDENTITY_ALREADY_REGISTERED`, carrying the stored `3GPP-AAA-Server-Name` |
| `USER_DEREGISTRATION`, `ADMINISTRATIVE_DEREGISTRATION`, `AUTHENTICATION_FAILURE`, `AUTHENTICATION_TIMEOUT` | Clear the AAA-server assignment and publish `AaaServerAssignmentChanged` | 2001, no user data |
| the same four, when the stored AAA server is **not** the requester | No state change | `IDENTITY_ALREADY_REGISTERED`, carrying the stored `3GPP-AAA-Server-Name` |
| the same four, when **nothing** is stored | No state change | `DIAMETER_UNABLE_TO_COMPLY` |
| anything else | — | `DIAMETER_UNABLE_TO_COMPLY` |

Unlike the MAR, a SAR carries no `AAA-Failure-Indication`, so a conflict has no
override path.

### MAR — Multimedia-Auth-Request

VoWiFi authentication:

- Schemes: `EAP-AKA` and `EAP-AKA'` (anything else → `AUTH_SCHEME_NOT_SUPPORTED`).
- `EAP-AKA'` additionally requires an ANID (missing → `MISSING_AVP`); CK'/IK' are
  derived via RFC 9048 and bound to the ANID.
- The answer carries CK(/CK'), IK(/IK'), XRES and `SIP-Authenticate = RAND‖AUTN`.
- **AAA-server conflict**: if a different AAA server is already assigned, the answer
  is `IDENTITY_ALREADY_REGISTERED` — **unless** the request carries an
  `AAA-Failure-Indication`, in which case the assignment is overwritten and the MAR
  proceeds.
- The first successful MAR stores the AAA-server assignment (Origin-Host/Origin-Realm
  into `location_vowifi`).
