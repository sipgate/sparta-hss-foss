# Security Policy

## Supported versions

sparta-hss is in active development. Only the latest release on Maven Central
receives fixes; there are no maintained backport branches.

## Reporting a vulnerability

Please do **not** open a public issue for a security problem.

Report it through GitHub's private vulnerability reporting: go to the
[Security tab](https://github.com/sipgate/sparta-hss-foss/security/advisories/new) and choose
"Report a vulnerability". This creates a private advisory visible only to you and the maintainers.

Useful details, where you have them:

- affected module and version
- a packet capture (PCAP) of the triggering exchange, or failing that a hex dump of the message
- what an attacker gains — crash, hang, memory exhaustion, spoofed identity, information disclosure

We will acknowledge the report and let you know whether we consider it in scope. Once a fix is
 released, we will credit you in the advisory unless you prefer otherwise.
