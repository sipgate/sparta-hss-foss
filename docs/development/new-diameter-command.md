# Implementing new Diameter commands

Diameter is implemented natively via the
[sparta-diameter](https://github.com/sipgate/sparta-diameter) client library
(`com.sipgate:sparta-diameter-3gpp-s6a`, `com.sipgate:sparta-diameter-3gpp-cxdx`,
`com.sipgate:sparta-diameter-3gpp-swx`). The HSS connects as a Diameter peer to the
configured DRA(s) (`sipgate.diameter.peers`) and registers one handler per supported
request type in `DiameterConnectionHandler`. The handlers live in
`com.sipgate.sparta.hss.diameter`, grouped by application (`s6a`, `cx`, `swx`) and
command.

Steps:

1. If the command's message model does not exist yet, add it to the
   `sparta-diameter` library.
2. Create a handler in the matching `com.sipgate.sparta.hss.diameter` package,
   implementing `RegisterableDiameterHandler<Request.In, Answer.Out>`.
3. Register the handler — in the Spring Boot deployment, declare it as a bean in the
   matching `*Configuration` (handlers are picked up via
   `ObjectProvider<RegisterableDiameterHandler<?, ?>>`); in an embedded deployment,
   register it with the `DiameterConnectionHandler`.
4. Write tests against the handler — the existing handler tests show the
   encode/decode setup.

A handler looks like this:

```java
public class FooHandler implements RegisterableDiameterHandler<FooRequest.In, FooAnswer.Out> {
    @Override
    public Class<FooRequest.In> requestType() {
        return FooRequest.In.class;
    }

    @Override
    @Transactional
    public CompletableFuture<FooAnswer.Out> handle(FooRequest.In request) {
        // business logic, then return the answer
        return CompletableFuture.completedFuture(answer);
    }
}
```

Outbound requests from a handler follow the event pattern: publish a `BaseEvent`
carrying the built request and let the outbound listener send it off the inbound
thread (see e.g. `InsertSubscriberDataOutboundListener`).
