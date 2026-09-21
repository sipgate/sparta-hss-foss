package com.sipgate.e2e;

import com.sipgate.e2e.agent.DiameterTestAgent;
import com.sipgate.e2e.agent.SharedDiameterAgentExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("E2E")
abstract class AbstractDiameterE2eTest {

    @RegisterExtension
    static final SharedDiameterAgentExtension SHARED_AGENT = new SharedDiameterAgentExtension();

    @BeforeEach
    void resetAgent() {
        agent().reset();
    }

    DiameterTestAgent agent() {
        return SharedDiameterAgentExtension.agent();
    }
}
