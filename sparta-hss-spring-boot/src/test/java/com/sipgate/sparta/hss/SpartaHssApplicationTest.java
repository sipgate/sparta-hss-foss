package com.sipgate.sparta.hss;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/// Boots the full application against an in-memory database and an unreachable Diameter peer.
/// Catches wiring errors: every bean of the batteries-included setup must be constructible.
@SpringBootTest
class SpartaHssApplicationTest {

    @Test
    void contextLoads() {
    }
}
