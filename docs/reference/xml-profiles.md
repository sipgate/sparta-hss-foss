# XML profiles

Two kinds of XML configuration drive the subscriber-facing answers:

- **Subscription-data profiles** — the S6a `Subscription-Data` and the SWx
  `Non-3GPP-User-Data` content, one file per profile
- **The Cx user-profile template** — the per-subscriber IMS user data

Both live under `/usr/local/etc/sparta-hss` in the container; a directory mounted
there replaces the shipped defaults entirely.

## Subscription-data profiles

Location: `sipgate.profileDir` (default `/usr/local/etc/sparta-hss/imsiProfiles`).

### Layout

```
imsiProfiles/
├── default.xml            # required
├── volte.xml              # named profiles
├── no-volte.xml
├── 214/                   # per-MCC overrides
│   └── no-volte.xml
└── 21401/                 # per-PLMN (MCC+MNC) overrides
    └── no-volte.xml
```

- The profile name is the file name without `.xml` (`volte.xml` → `volte`).
- `default.xml` must exist; its absence fails the boot.
- PLMN-specific resolution for a visited PLMN, in order of precedence:
  1. `<vplmnId>/<profile>.xml`
  2. `<mcc>/<profile>.xml`
  3. `<profile>.xml`
- The same directory feeds **both** S6a (ULR/ISD answers) and SWx (registration
  answers): a file is encoded as `Subscription-Data` for S6a and as
  `Non-3GPP-User-Data` for SWx.

### S6a format

A profile is a `<SubscriptionData>` document; every element is mapped to the
corresponding AVP. Supported elements:

| Element | AVP |
|---------|-----|
| `SubscriberStatus` | Subscriber-Status (`serviceGranted`, `operatorDeterminedBarring`) |
| `NetworkAccessMode` | Network-Access-Mode (`packet-and-circuit`, `only-circuit`, `only-packet`) |
| `MSISDN` | **ignored** — the MSISDN AVP is always taken from the subscriber database |
| `APNConfigurationProfile` | APN-Configuration-Profile |
| `APNConfiguration` | APN-Configuration |
| `ContextIdentifier` | Context-Identifier |
| `AllAPNConfigIncluded` | All-APN-Configurations-Included-Indicator (`yes`, `modified`) |
| `PDNType` | PDN-Type (`ipv4`, `ipv6`, `ipv4v6`, `ipv4-or-ipv6`) |
| `ServiceSelection` | Service-Selection (APN) |
| `EpsSubscribedQosProfile` | EPS-Subscribed-QoS-Profile |
| `QosClassIdentifier` | QoS-Class-Identifier (numeric) |
| `AllocationRetentionPriority` | Allocation-Retention-Priority |
| `PriorityLevel` | Priority-Level |
| `PreemptionCapability` / `PreemptionVulnerability` | Pre-emption-Capability / -Vulnerability (`yes`/`no`) |
| `AMBR` | AMBR |
| `MaxRequestedBandwithUL` / `MaxRequestedBandwithDL` | Max-Requested-Bandwidth-UL / -DL (bit/s) |
| `VPlmnDynamicAddrAllowed` | VPLMN-Dynamic-Address-Allowed (`yes`/`no`) |
| `SIPTOPermission` | SIPTO-Permission (`yes`/`no`) |
| `LIPAPermission` | LIPA-Permission (`prohibited`, `only`, `conditional`) |
| `PDNGWAllocationType` | PDN-GW-Allocation-Type (`static`, `dynamic`) |
| `ChargingCharacteristics` | Charging-Characteristics-3GPP |
| `APNOIReplacement` | APN-OI-Replacement |
| `RATFreqSelPriorityID` | RAT-Frequency-Selection-Priority-ID |
| `MIP6AgentInfo` / `MIPHomeAgentHost` / `MIPHomeAgentAddress` / `DestinationRealm` / `DestinationHost` | P-GW pinning (MIP6-Agent-Info inside APN-Configuration) |

**Unknown elements fail the boot** — a profile change cannot silently drop
subscription data on the wire.

Example (shipped `default.xml` excerpt):

```xml
<SubscriptionData vendor="10415">
    <SubscriberStatus vendor="10415">serviceGranted</SubscriberStatus>
    <MSISDN vendor="10415">will-be-replaced-by-hss</MSISDN>
    <NetworkAccessMode vendor="10415">packet-and-circuit</NetworkAccessMode>
    <APNConfigurationProfile vendor="10415">
        <ContextIdentifier vendor="10415">1</ContextIdentifier>
        <AllAPNConfigIncluded vendor="10415">yes</AllAPNConfigIncluded>
        <APNConfiguration vendor="10415">
            <ContextIdentifier vendor="10415">1</ContextIdentifier>
            <PDNType vendor="10415">ipv4</PDNType>
            <ServiceSelection>internet</ServiceSelection>
        </APNConfiguration>
        <APNConfiguration vendor="10415">
            <ContextIdentifier vendor="10415">2</ContextIdentifier>
            <SIPTOPermission vendor="10415">no</SIPTOPermission>
            <PDNType vendor="10415">ipv4v6</PDNType>
            <ServiceSelection>ims</ServiceSelection>
            <VPlmnDynamicAddrAllowed vendor="10415">no</VPlmnDynamicAddrAllowed>
        </APNConfiguration>
    </APNConfigurationProfile>
</SubscriptionData>
```

The `vendor` attributes are decorative — the encoder derives the AVP vendor from the
element. The `MSISDN` placeholder is ignored; the real MSISDN (TB-encoded) is
prepended to the Subscription-Data AVP from the database.

## Cx user-profile template

Location: `sipgate.ims.userProfile.path`
(default `/usr/local/etc/sparta-hss/user-profile.xml`).

A single template with two placeholders, substituted per request when a Cx
registration is answered:

- `{privateId}` — the IMSI (Private-Identity)
- `{msisdn}` — the subscriber's MSISDN

```xml
<IMSSubscription>
    <PrivateID>{privateId}</PrivateID>
    <ServiceProfile>
        <PublicIdentity>
            <Identity>tel:+{msisdn}</Identity>
        </PublicIdentity>
    </ServiceProfile>
</IMSSubscription>
```

The template is validated against the JAXB model at startup; the shipped one declares
no application server. To route IMS traffic, add an `ApplicationServer` block with
the S-CSCF-relevant public service identities.
