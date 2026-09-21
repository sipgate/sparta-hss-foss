package com.sipgate.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.diameter.base.session.PeerState;
import org.junit.jupiter.api.Test;

class HarnessConnectivityE2eTest extends AbstractDiameterE2eTest {

    @Test
    void hss_connects_to_the_agent() {
        assertThat(agent().peerState()).isEqualTo(PeerState.R_OPEN);
    }
}
