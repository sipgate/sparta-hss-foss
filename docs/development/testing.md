# Testing

There are three tiers of tests.

## Unit tests

Default `./mvnw test` / `./mvnw verify` run. Cover the AKA engine (including 3GPP
reference vectors), every Diameter handler, the DAOs (H2 in-memory) and the services.
Fast, no infrastructure.

## Spring integration tests

Boot the Spring context — verifying auto-configuration, capability toggling, the
HTTP endpoints and a real (H2) persistence round-trip. They carry no tag, so they
run in the default `./mvnw verify` next to the unit tests; only the
[E2E suite](#e2e) is excluded.

To run a single class, tell Surefire not to fail in the module that has no match —
the reactor spans two modules and `-Dtest` is applied to both:

```shell
./mvnw test -Dtest=SpartaHssApplicationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Representative classes: `SpartaHssApplicationTest` (context starts),
`InterfaceToggleTest` (capabilities load/unload handlers),
`HttpResourceToggleTest` (admin endpoints behind the opt-in properties),
`SqliteDatabaseTest`, `AuthenticatorOverrideTest`.

## E2E

The containerized suite: a real HSS image, a seeded SQLite database, and a Diameter
test agent driving the actual wire protocol.

```shell
make run-e2e-tests
make run-e2e-single TEST='CxE2eTest$$Sar'                          # one class / nested class
make run-e2e-single TEST='CxE2eTest$$Sar#some_method'              # one method
```

The stack (defined in `docker-compose.e2e.yml`):

1. `seed-db` — seeds a SQLite file with test subscribers (raw OP values; the HSS is
   configured with `SIPGATE_AUC_STORED_KEY_FORMAT=op` to match)
2. `hss` — builds and runs the HSS image, connecting *outbound* to the test agent
3. `e2e-tests` — a Maven container with the `@Tag("E2E")` suite; a Diameter test
   agent (`DiameterTestAgent`) listens on port 3869, acts as the "DRA" and
   asserts on the answers

Suites:

- `CxE2eTest` — SAR (registration, de-registration, RTR), MAR (vector, resync)
- `S6aE2eTest` — AIR (vectors, resync, KASME), ULR (location, profile, roaming),
  PUR, NOR, ISD push
- `HarnessConnectivityE2eTest` — connect/disconnect behaviour of the Diameter client

The E2E suite is the only tier excluded from `./mvnw verify` (`@Tag("E2E")`); it runs
in CI via `make run-e2e-tests`.
