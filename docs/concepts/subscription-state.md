# Subscription state

Every SIM has an active flag: the `sim.isActive` DB column. An inactive SIM is denied
service on all interfaces (4G, IMS, VoWiFi) and loses its live registrations.
Whether the HSS enforces the flag at all is a configuration switch.

> **Summary:** The `sim.isActive`-flag (default: inactive)
> set through the `SubscriptionStateService` (`activate`/`deactivate` by ICCID);
> deactivating a SIM also ends the live registrations
> on every enabled interface. Whether the HSS enforces the flag is selected by
> `sipgate.access-policy`: `subscription-state` rejects an inactive SIM, `allow-all` stores
> and updates the flag but rejects nothing.


## The active flag

One flag per SIM, effective for all its IMSIs. The column is `NOT NULL` with
default "not active" (see the [database reference](../reference/database.md#sim)).

The flag is changed through the
[`SubscriptionStateService`](../development/extending.md#subscriptionstateservice)
(`activate` / `deactivate`, by ICCID). A derived implementation may implement
a REST endpoint or Kafka listener for example to trigger the state.

After persisting the new state, the service publishes a `SimActivated` or
`SimDeactivated` event. Several listeners are shipped that react to the event.
Several listeners are shipped with the FOSS-HSS.
For example: Deactivation ends the registrations of all IMSIs of the SIM:

| Interface | Message | Sent to |
|-----------|---------|---------|
| S6a/S6d | Cancel-Location, `SUBSCRIPTION_WITHDRAWAL` | the stored MME |
| Cx/Dx | Registration-Termination, `PERMANENT_TERMINATION` | the stored S-CSCF |
| SWx | Registration-Termination, `PERMANENT_TERMINATION` | the stored 3GPP AAA server |

Like all outbound requests of this HSS, these are sent fire-and-forget; the stored
location rows are removed in any case.

> **Note:** With the `allow-all`-Policy (see below) the listeners skip
> the signalling, because the SIM is always allowed.


## The Access-Policy

[`sipgate.access-policy`](../reference/configuration.md#authentication) selects
the [`AccessPolicy`](../development/extending.md#accesspolicy). You MUST set that property,
or the HSS does not start. The shipped options are:

`subscription-state`
: (shipped `application.yaml`): a request is allowed only if the SIM is active in the DB.

`allow-all`
: every subscriber is allowed; flags are still stored and updated, but nothing is rejected by them


## Behavior at the interfaces

A denied subscriber is rejected with the following results:

| Command | Interface | Denial (inactive SIM) | No decision |
|---------|-----------|-----------------------|-------------|
| AIR | S6a/S6d | 5420 `ERROR_UNKNOWN_EPS_SUBSCRIPTION` (no Error-Diagnostic) | 5012 `DIAMETER_UNABLE_TO_COMPLY` |
| ULR | S6a/S6d | 5420 `ERROR_UNKNOWN_EPS_SUBSCRIPTION` | 5012 `DIAMETER_UNABLE_TO_COMPLY` |
| MAR | Cx/Dx | 5001 `ERROR_USER_UNKNOWN` | 5012 `DIAMETER_UNABLE_TO_COMPLY` |
| SAR (Registration) | Cx/Dx | 5001 `ERROR_USER_UNKNOWN` | 5001 `ERROR_USER_UNKNOWN` |
| MAR | SWx | 5450 `ERROR_USER_NO_NON_3GPP_SUBSCRIPTION` | 5012 `DIAMETER_UNABLE_TO_COMPLY` |
| SAR (Registration) | SWx | 5450 `ERROR_USER_NO_NON_3GPP_SUBSCRIPTION` | 5012 `DIAMETER_UNABLE_TO_COMPLY` (Experimental-Result) |


## Rollout

The rollout never interrupts active subscribers:

1. Run with `allow-all` while the source-of-truth system replays the states via `SubscriptionStateService.activate`.
  This prevents the HSS from flooding the network with Registration-Termination messages.
2. Verify, then switch to `subscription-state`.

> **Warning**: The `allow-all`-Policy might not be compatible with your system. For example:
> If the HSS always allows a subscriber, but the PGW queries the actual subscription state differently
> the MME might not be able to attach the subscriber and react with a PurgeUE, retrying the
> authentication.


## Upgrade from 1.0.x

Version 2.0.0 changes the schema:

- The column `imsi.state` is dropped, `sim.isActive` is added. A reference migration ships in
  [`db/spartahss-02-sim_isactive.sql`](https://github.com/sipgate/sparta-hss-foss/blob/main/db/spartahss-02-sim_isactive.sql).
  Nothing needs to be carried over; `imsi.state` was never read by the HSS.
- The new column defaults to inactive.
- Consequence: with the shipped `subscription-state` policy, an upgraded HSS
  rejects every subscriber until the flags are set. Upgrade with
  `sipgate.access-policy=allow-all`, replay the states (see [rollout](#rollout)), then switch.
