# Authentication

sparta-hss performs UMTS AKA (3GPP TS 33.102) with the Milenage algorithm set
(TS 35.206: functions f1, f1*, f2, f3, f4, f5, f5*). The implementation is checked
against the conformance test data in TS 35.208.

## Key material

Per SIM, stored in the `sim` table:

- `secretKey` (Ki) — the 16-byte subscriber key
- `op` — the operator key column, interpreted according to
  [`sipgate.auc.stored-key-format`](../reference/configuration.md#authentication):
  the value `opc` (default, TS 35.205 recommendation) means the column holds the
  pre-computed **OPc**; the value `op` means it holds **OP**, from which OPc is
  derived on every authentication
- `sqn` — the sequence number, advanced with every vector

## Vector generation

For each requested vector:

1. The SQN is advanced (increment +2, never below 32).
2. A 16-byte RAND is generated.
3. The Milenage functions compute MAC-A (f1), XRES (f2), CK (f3), IK (f4), AK (f5).
4. `AUTN = SQN⊕AK ‖ AMF ‖ MAC-A` (16 bytes).
5. For E-UTRAN, **KASME** is derived (TS 33.401 Annex A.2, HMAC-SHA256 with FC 0x10)
   from (CK, IK, SQN⊕AK, SN_id of the visited PLMN).

An AIR carries a Visited-PLMN-Id in practice, and the S6a handler requires one: a
request without it is rejected with `UNKNOWN_EPS_SUBSCRIPTION` before any vector is
generated. The Cx and SWx MAR paths generate 3G vectors and derive no KASME.

The AMF is scheme-dependent and supplied by the caller:

| Scheme            | AMF     | Note                                              |
|-------------------|---------|---------------------------------------------------|
| E-UTRAN (AIR)     | `0x8000`| separation bit set (TS 33.401 6.1.1)              |
| UTRAN/GERAN (AIR) | `0x0000`| no separation-bit requirement found in the specs  |
| IMS-AKAv1-MD5 (MAR)| `0x0000`| TS 33.203 §6.1                             |
| EAP-AKA (SWx MAR) | `0x0000`| RFC 4187                                         |
| EAP-AKA' (SWx MAR)| `0x8000`| separation bit mandated (RFC 9048 §3.3)           |

For **EAP-AKA'** the CK/IK pair is further reduced to CK'/IK' via the RFC 9048 key
derivation (FC 0x20), bound to the ANID (access-network identity) — an SWx MAR for
EAP-AKA' without ANID is rejected.

## Vector count

The HSS answers with **one vector** per requested RAT, regardless of the requested
count. This is a protection mechanism: the requested count is simply not trusted,
and the spec allows the HSS to return fewer vectors than requested — so it returns
the minimum.

## Re-synchronisation

When the UE and HSS disagree on the SQN, the UE answers with AUTS
(`SQN_MS⊕AK* ‖ MAC-S`). The HSS:

1. recovers `SQN_MS = AUTS[0..5] ⊕ f5*(Ki, RAND, OPc)`,
2. recomputes `MAC-S = f1*(Ki, RAND, OPc, SQN_MS, AMF=0x0000)` and compares it in
   constant time,
3. on a match, adopts `max(SQN_MS + 10, current SQN)` — the `+ 10` is a safety
   margin, and the `max` keeps the stored SQN from stepping backwards.

A mismatch fails the authentication; the attempt/success/failure counts are exposed
as `milenage_resync_sqn_*` metrics.

## Schemes per interface

| Interface | Command | Scheme            | Vector content                                   |
|-----------|---------|-------------------|--------------------------------------------------|
| S6a/S6d   | AIR     | EPS-AKA           | E-UTRAN vectors: RAND, XRES, AUTN, KASME        |
| S6a/S6d   | AIR     | UTRAN/GERAN AKA   | UTRAN vectors: RAND, XRES, AUTN, CK, IK          |
| Cx/Dx     | MAR     | AKAv1-MD5         | 3G vector + CK/IK (SIP-Authenticate = RAND‖AUTN) |
| SWx       | MAR     | EAP-AKA / EAP-AKA'| 3G vector; CK'/IK' for EAP-AKA'                |

An AIR that requests re-synchronisation for **both** E-UTRAN and UTRAN/GERAN in the
same request is rejected with `DIAMETER_UNABLE_TO_COMPLY`, without any vectors.
