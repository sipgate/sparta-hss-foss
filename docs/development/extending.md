# Extending the core

`sparta-hss-base` is published as a library and designed to be embedded in your own
application. The Spring Boot module exists as the reference deployment, not as a
requirement.

## Modules

The project is a two-module Maven build — see [Architecture](architecture.md#modules)
for the layout. Both modules are published to Maven Central with sources and Javadoc
jars: `com.sipgate:sparta-hss-base` (the core) and `com.sipgate:sparta-hss-spring-boot`
(the runnable app).

## Transaction contract

Handlers and services demarcate their transaction boundaries with
`@jakarta.transaction.Transactional` (REQUIRED semantics, rollback on unchecked
exceptions). **The hosting application must run them in a container that honors that
annotation** — Spring and CDI containers do. Instances constructed directly with
`new` are *not* transactional; an embedder without a container must wrap the calls in
transactions itself.

## Extension points

The base core exposes three extension points. In the Spring Boot deployment each is
wired as a `@ConditionalOnMissingBean` — defining your own bean replaces the default.

### `EventPublisher`

Handlers and services publish domain events (outbound CLR/ISD/RTR requests,
`LocationUpdated`, `UeVisited`, ...) through this interface instead of talking to
listeners directly. Your implementation decides the dispatch semantics — which
listeners receive an event, and on which thread. Events carrying outbound Diameter
requests must reach their outbound listener **off the inbound request thread**.

Default (Spring Boot): Spring's `ApplicationEventPublisher`, so listeners register
with plain `@EventListener`.

### `Authenticator`

Produces the AKA vectors (4G and 3G) from a `MilenageInput` (AMF, optional resync
info, SIM entity). The default implementation is
`MilenageAuthenticator` (Milenage, see [Authentication](../concepts/authentication.md)).
Replace it for a different algorithm set or to delegate generation elsewhere (e.g. a
separate authentication server).

### `EutranAccessPolicy`

Decides whether a subscriber may access E-UTRAN in a visited network:

```java
@FunctionalInterface
public interface EutranAccessPolicy {
    boolean isEutranAccessAllowed(String imsi, String mcc);
}
```

Consulted by the AIR handler before issuing E-UTRAN vectors; a denied subscriber
cannot authenticate for 4G in that network. Default: allow all. If you have
different 4G access policies, they can be implemented here.

## Embedding without Spring

A minimal non-Spring embedding needs:

1. A container honoring `@Transactional` (or manual transaction demarcation)
2. An `EntityManager`/DAO layer pointed at your database
3. Implementations of the three extension points
4. Construction of the handlers and a Diameter client (see how
   `DiameterClientConfiguration` does it in the Spring Boot module)
5. Calling `init()` on the `SubscriptionDataFactory`/`Non3gppUserDataFactory` once
   after construction, before the first request

For most use cases the Spring Boot module with its `@ConditionalOnMissingBean`
defaults is the shorter path.
