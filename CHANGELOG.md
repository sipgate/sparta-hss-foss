# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Cx and SWx handlers for authentication and server assignment check the AccessPolicy interface before allowing access to the IMS core.
- `SubscriptionStateService` to activate/deactivate a SIM by ICCID. Deactivation flips `sim.isActive` and publishes 
  one `SimDeactivated` event; each enabled interface's outbound listener then detaches the subscriber: 
  S6a CLR `SUBSCRIPTION_WITHDRAWAL` to the stored MME, Cx RTR `PERMANENT_TERMINATION` to the stored S-CSCF, 
  SWx RTR `PERMANENT_TERMINATION` to the stored 3GPP AAA server, then the registration rows are removed. 
  Fire-and-forget like all outbound requests; repeated deactivation is idempotent.

### Changed

- Added method `isImsAccessAllowed` to `EutranAccessPolicy` (also: removed `@FunctionalInterface`)
- Renamed `EutranAccessPolicy` to `AccessPolicy` (also: moved to another package)
- Renamed configuration property `sipgate.eutran-access-policy` to `sipgate.access-policy`.
- Renamed metric `eutran_access_rejected` to `access_rejected` in order to include IMS metrics

### Fixed

- S6a ULR: the MSISDN is resolved before the `AccessPolicy` runs, so an unknown IMSI answers `ERROR_USER_UNKNOWN` instead of
  the policy's `DIAMETER_UNABLE_TO_COMPLY` (the default `subscription-state` policy cannot decide for an IMSI without a SIM).

## [2.0.0] - 2026-10-28

> **Important:** You need to carefully migrate your database. 
> The Column `Imsi.state` is dropped and the column `Sim.isActive` is added
> with a default of `0`. If you do not migrate carefully you might end up with
> a bunch of inactive SIMs

### Added

- Column `isActive` for SIMs for new implementation of `EutranAccessPolicy`. 
- Metric `eutran_access_rejected` (`command`, `mcc`, `reason`) counting AIR/ULR
  rejections by the `EutranAccessPolicy`.
- added `sipgate.eutran-access-policy` property. 

### Removed

- Unused column `state` for IMSI entities.

## [1.0.0] - 2026-09-22

### Added

- Open-source release of sipgate's HSS as two modules: `sparta-hss-base`
  (Diameter Cx/Dx, S6a/S6d and SWx request handling, Milenage AKA
  authentication, JPA persistence entities) and `sparta-hss-spring-boot` (the
  runnable Spring Boot application, zero-setup SQLite default, `/health`
  live/ready and `/prometheus` endpoints).
- IP-SM-GW persistence: the serving node announced in a Cx SAR registration
  (UCN) is stored, so SMS-over-IP routing survives restarts.
- Reference DDL (`db/`) for the entity tables, plus an idempotent migration
  for the new `location_ip_sm_gw` table.
- Docker image with multi-arch (amd64/arm64) support, an unprivileged runtime
  user, persistent data volume at `/var/lib/sparta-hss` and overridable
  default Cx/S6a profiles under `/usr/local/etc/sparta-hss`.
- Containerized E2E test suite (`make run-e2e-tests`): a Diameter test agent
  drives the containerized HSS against a seeded SQLite database, covering Cx
  (SAR, MAR, RTR), S6a (AIR, ULR, ISD, NOR, PUR) and harness connectivity.
  Individual cases run via `make run-e2e-single TEST=...`.

[unreleased]: https://github.com/sipgate/sparta-hss-foss/compare/2.0.0...HEAD
[2.0.0]: https://github.com/sipgate/sparta-hss-foss/compare/1.0.0...2.0.0
[1.0.0]: https://github.com/sipgate/sparta-hss-foss/compare/eb1f9e6...1.0.0
