# Architecture

## Modules

sparta-hss is a two-module Maven project:

| Module                 | Role                                                                    |
|------------------------|-------------------------------------------------------------------------|
| `sparta-hss-base`      | The framework-agnostic HSS core: Diameter handlers, AKA engine, JPA entities and DAOs, services |
| `sparta-hss-spring-boot` | The runnable Spring Boot application on top of the core: auto-configuration, HTTP interface, actuator |

Both modules are published to Maven Central; the core is usable as a library in its
own right (see [Extending the core](extending.md)).

## Topology

```
S-CSCF ──Cx/Dx──▶ DRA ◀──S6a/S6d── MME
                     ▲
                     │  outbound Diameter (the HSS initiates)
                 sparta-hss ──SWx── 3GPP AAA Server
                     │
                     ▼
                 database (subscriber, SIM, location, roaming state)
```

The HSS connects **outbound** to its configured peers and registers one handler per
supported request type. An inbound request is answered by the matching handler with
a `CompletableFuture`; the answer goes back to the peer when the future completes.
Everything the HSS needs to send back out (CLR, ISD, RTR) is published as a domain
event and dispatched on a dedicated outbound thread pool, off the inbound request
thread.

## Interfaces as a deployment decision

The enabled interfaces are a deployment property
(`sipgate.diameter.capabilities`, see [Diameter setup](../getting-started/diameter-setup.md)).
A missing capability causes the relevant handler and its supporting beans not to be
instantiated, and the application is not advertised in the CER — an interface that is
not in the list is simply not part of that deployment.

## Request lifecycle (inbound)

1. The Diameter request arrives on a peer connection (Netty event loop).
2. The connection layer hands it to the registered handler for its request type
   (e.g. `AuthenticationInfoHandler` for AIR), running it on an inbound worker pool
   so blocking work (database, event dispatch) never occupies the event loop that
   also drives the connection watchdog.
3. The handler runs in a transaction: it loads the SIM, generates or looks up data,
   records metrics, and may write location state.
4. The handler returns the answer as a `CompletableFuture`. When the future
   completes, the connection layer sends the answer back to the peer. A business
   error (e.g. unknown IMSI) is an answer with the appropriate result code; an
   unexpected failure becomes `DIAMETER_UNABLE_TO_COMPLY` (5012).

## Outbound requests

Outbound requests (CLR, ISD, RTR) follow the event path: the handler publishes a
`BaseEvent` carrying the built request, and a listener on the outbound pool sends it.

The HSS does **not** wait for the answer to these requests — the response is logged,
that's it. Some flows technically expect the HSS to wait (e.g. the ULA should only
be sent after the ISD answer has been received). sparta-hss deliberately does not
wait: in most cases waiting only makes the transaction slower with no benefit.

## Domain events

Beyond the outbound-request events, the core publishes domain events:

- `LocationUpdated` — a ULR was processed (IMSI, MSISDN, visited MCC/MNC, IMEI)
- `UeVisited` — a UE visited a TAC with a given effective profile
- `ScscfAssignmentChanged` — the S-CSCF assignment changed (Cx)
- `AaaServerAssignmentChanged` — the 3GPP AAA server assignment changed (SWx)

In the Spring Boot deployment these are plain Spring application events
(`@EventListener`); an embedder supplies its own
[EventPublisher](extending.md#eventpublisher).

These events are also the extension mechanism: if you are building your own variant
of the HSS and need to react to something happening (a subscriber moving, a
registration changing, ...), listening to the events is the way to do it. At sipgate
they drive proprietary business processes around the core in a separate module that
we did not open source. An example of this is triggering an SMS if a roaming event
is detected for the first time in a given time frame.

## Persistence

All state is in the database (14 tables, see [Database reference](../reference/database.md)):

- subscriber identity: `imsi`, `msisdn`, `sim` (key material, SQN)
- profile assignment: `imsi_profile`, `tac_profile`
- serving-node state: `imsi_scscf`, `location_lte`, `location_vowifi`, `location_ip_sm_gw`
- roaming rules and block events: five `roaming_blocked_*` tables

The default deployment uses SQLite for zero setup; production deployments point at a
client/server database.
