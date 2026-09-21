# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[unreleased]: https://github.com/sipgate/sparta-hss-foss/compare/eb1f9e6...HEAD
