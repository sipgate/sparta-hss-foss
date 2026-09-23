# sparta-hss

sparta-hss is the open-source release of sipgate's Home Subscriber Server (HSS) for
3GPP mobile networks. It authenticates subscribers, holds their subscription data, and
tracks their location — answering Diameter requests from MMEs, S-CSCFs and 3GPP AAA
servers.

| Interface | Standard        | Peer             | Purpose                                          |
|-----------|-----------------|------------------|--------------------------------------------------|
| Cx/Dx     | 3GPP TS 29.228  | S-CSCF           | IMS registration, user data, IMS authentication  |
| S6a/S6d   | 3GPP TS 29.272  | MME / SGSN       | 4G authentication, location, subscription data   |
| SWx       | 3GPP TS 29.273  | 3GPP AAA Server  | VoWiFi registration and authentication           |

## What sparta-hss does

- **Milenage AKA authentication** — E-UTRAN, UTRAN/GERAN, IMS-AKAv1-MD5 and EAP-AKA/EAP-AKA'
  vectors with SQN re-synchronisation
- **Subscriber profile management** — per-IMSI and per-TAC profile assignment with
  per-PLMN profile overrides
- **Location tracking** — MME, S-CSCF, IP-SM-GW and 3GPP AAA server assignments, including
  the outbound CLR/RTR signalling when a UE moves
- **Roaming blocking** — per-network and per-IMSI block rules with an emergency MCC
  allowlist
- **Observability** — health indicators and a rich set of Micrometer/Prometheus metrics

## Documentation map

| Section         | What you find there                                                        |
|-----------------|----------------------------------------------------------------------------|
| Getting started | Run the Docker image, deploy, configure the Diameter peers                 |
| Concepts        | How the pieces fit together: authentication, profiles, roaming             |
| Reference       | Every configuration property, HTTP endpoint, metric, table and AVP profile |
| Guides          | Re-profiling at scale, managing roaming rules, monitoring                  |
| Development     | Architecture, build, test, and extend the core as a library                |

## Requirements

- Run: any container runtime (or a plain JVM — see [Extending the core](development/extending.md))
- Build: Java 25, Maven (a [wrapper](https://maven.apache.org/wrapper/) is included)

## License

MIT — see [LICENSE](https://github.com/sipgate/sparta-hss-foss/blob/main/LICENSE).
