# Database

The HSS keeps all state in 14 tables. A reference DDL (MySQL dump) is shipped in
[`db/spartahss-00-ddl.sql`](https://github.com/sipgate/sparta-hss-foss/blob/main/db/spartahss-00-ddl.sql).
With the zero-setup default the schema is created and updated automatically
(`ddl-auto: update`); for a production database manage the schema yourself and set
`ddl-auto: none`.

Table and column names are taken from the entities **as written** (no camelCase →
snake_case translation).

## Subscriber identity

### `imsi`

| Column | Type | Description |
|--------|------|-------------|
| `id` | int, PK, auto | |
| `imsi` | char(15), unique | The IMSI |
| `simId` | int, FK → `sim.id` | The SIM, `NULL`-able |
| `state` | enum | `inactive`, `pending`, `active`, `active_deprecated`, `obsolete` |
| `type` | enum | `main` or `dyn` (dynamic) |
| `assignedAt` | timestamp | |

### `msisdn`

| Column | Type | Description |
|--------|------|-------------|
| `id` | int unsigned, PK, auto | |
| `msisdn` | varchar(45), unique | The MSISDN (digits) |

### `sim`

| Column | Type | Description |
|--------|------|-------------|
| `id` | int unsigned, PK, auto | |
| `msisdn` | int unsigned, FK → `msisdn.id` | The subscriber number |
| `secretKey` | binary(16) | Ki, the subscriber key |
| `sqn` | bigint | Sequence number (default 0) |
| `op` | binary(16) | OPc or OP, depending on [`sipgate.auc.stored-key-format`](configuration.md#authentication) |
| `ss_cw` | enum | `active` / `inactive` |
| `iccid` | char(20) | |

## Profile assignment

### `imsi_profile`

| Column | Type | Description |
|--------|------|-------------|
| `imsiId` | int, PK, FK → `imsi.id` | One profile per IMSI |
| `name` | varchar(255) | Profile name (file name in `profileDir` without `.xml`) |
| `lastUpdate` | timestamp | |

### `tac_profile`

| Column | Type | Description |
|--------|------|-------------|
| `tac` | varchar(8), PK | The TAC (first 8 digits of the IMEI) |
| `name` | varchar(255) | Profile name |
| `lastUpdate` | timestamp | |

## Serving-node state

One row per IMSI; updated on registration, cleared on de-registration.

### `imsi_scscf`

The S-CSCF assigned to the IMSI (Cx).

| Column | Type | Description |
|--------|------|-------------|
| `imsiId` | int, PK, FK → `imsi.id` | |
| `scscf` | varchar(255) | The S-CSCF name (Server-Name) |
| `diameter_host` | varchar(255) | Diameter host of the S-CSCF (routing for RTR) |
| `diameter_realm` | varchar(255) | Diameter realm of the S-CSCF |
| `lastUpdate` | timestamp | |

### `location_lte`

The current MME location (S6a).

| Column | Type | Description |
|--------|------|-------------|
| `imsiId` | int, PK, FK → `imsi.id` | |
| `mmeHostname` | varchar(255) | MME Origin-Host |
| `mmeRealm` | varchar(255) | MME Origin-Realm |
| `visitedPlmnId` | varchar(255) | Visited PLMN (MCC+MNC digits) |
| `tac` | varchar(8) | TAC from the IMEI |
| `lastUpdate` | timestamp | |

### `location_vowifi`

The 3GPP AAA server assigned to the IMSI (SWx).

| Column | Type | Description |
|--------|------|-------------|
| `imsiId` | int, PK, FK → `imsi.id` | |
| `aaa_server_name` | varchar(255) | AAA server name |
| `diameter_host` | varchar(255) | AAA server Diameter host |
| `diameter_realm` | varchar(255) | AAA server Diameter realm |
| `lastUpdate` | timestamp | |

### `location_ip_sm_gw`

The IP-SM-GW (Serving-Node from SAR) assigned to the IMSI — persists across restarts
so SMS-over-IP routing survives.

| Column | Type | Description |
|--------|------|-------------|
| `imsiId` | int, PK, FK → `imsi.id` | |
| `ipSmGwName` | varchar(255) | IP-SM-GW name |
| `ipSmGwRealm` | varchar(255) | IP-SM-GW realm |
| `lastUpdate` | timestamp | |

## Roaming

### `roaming_blocked_location_lte`

Network-level block rule by PLMN.

| Column | Type | Description |
|--------|------|-------------|
| `id` | int unsigned, PK, auto | |
| `mcc` | varchar(3) | |
| `mnc` | varchar(3) | |
| `reason` | varchar(255) | |

Unique on (`mcc`, `mnc`).

### `roaming_blocked_location`

Network-level block rule by GT prefix.

| Column | Type | Description |
|--------|------|-------------|
| `id` | int unsigned, PK, auto | |
| `gt_prefix` | varchar(20), unique | |
| `reason` | varchar(255) | |

### `roaming_blocked_imsi_override_lte`

Per-IMSI roaming decision by PLMN.

| Column | Type | Description |
|--------|------|-------------|
| `id` | int unsigned, PK, auto | |
| `imsi` | char(20) | |
| `mcc` | varchar(3) | |
| `mnc` | varchar(3) | |
| `roaming` | char(10) | `allowed`, `blocked`, `default` |
| `reason` | varchar(255) | |

Unique on (`imsi`, `mcc`, `mnc`).

### `roaming_blocked_imsi_override`

Per-IMSI roaming decision by GT prefix — same shape, with `gt_prefix` instead of
MCC/MNC. Unique on (`imsi`, `gt_prefix`).

### `roaming_blocked_event`

A blocked ULR that has not yet been allowed again. Expired rows (older than 5 minutes)
are turned into the `roaming_blocked_not_recovered` metric and deleted.

| Column | Type | Description |
|--------|------|-------------|
| `id` | int unsigned, PK, auto | |
| `imsi` | char(20) | |
| `mcc` | varchar(20) | |
| `component` | varchar(20) | Always `hss` |
| `created_at` | timestamp(3) | |

## Seed data

An example seed for a local/E2E setup (IMSI/SIM/MSISDN rows plus profile assignments)
is shipped in
[`docker/e2e/seed.sql`](https://github.com/sipgate/sparta-hss-foss/blob/main/docker/e2e/seed.sql).
Note it stores raw **OP** values — a HSS reading it must be configured with
`SIPGATE_AUC_STORED_KEY_FORMAT=op`.
