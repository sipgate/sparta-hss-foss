package com.sipgate.sparta.hss.diameter.client;

import com.sipgate.sparta.diameter.base.session.PeerState;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;


@Component
public class DiameterConnectionHealthIndicator implements HealthIndicator {


    private final DiameterConnectionHandler connectionHandler;

    public DiameterConnectionHealthIndicator(final DiameterConnectionHandler connectionHandler) {
        this.connectionHandler = connectionHandler;
    }

    @Override
    public Health health() {
        final var peerStates = connectionHandler.getPeerStates();
        final var healthBuilder = peerStates.values().stream().anyMatch(state -> state == PeerState.I_OPEN)
                ? Health.up()
                : Health.down();

        return healthBuilder.withDetail("peerStates", peerStates).build();
    }
}

