package com.sipgate.e2e.agent;

import java.time.Duration;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;

public class SharedDiameterAgentExtension implements BeforeAllCallback {

    private static final Namespace NAMESPACE = Namespace.create(SharedDiameterAgentExtension.class);
    private static final String KEY = "shared-agent";

    static final String AGENT_HOST = "dra.e2e.epc.mnc001.mcc001.3gppnetwork.org";
    static final String AGENT_REALM = "epc.mnc001.mcc001.3gppnetwork.org";

    private static volatile DiameterTestAgent agent;

    @Override
    public void beforeAll(final ExtensionContext context) {
        final var holder = context.getRoot().getStore(NAMESPACE).getOrComputeIfAbsent(KEY, ignored -> {
            final var port = Integer.getInteger("e2e.agent.port", 3869);
            final var started = new DiameterTestAgent(AGENT_HOST, AGENT_REALM).start(port);
            started.awaitConnected(Duration.ofSeconds(60));
            return new Holder(started);
        }, Holder.class);
        agent = holder.agent;
    }

    public static DiameterTestAgent agent() {
        return agent;
    }

    private record Holder(DiameterTestAgent agent) implements CloseableResource {
        @Override
        public void close() {
            agent.close();
        }
    }
}
