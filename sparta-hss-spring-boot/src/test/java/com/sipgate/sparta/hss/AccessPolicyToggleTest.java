package com.sipgate.sparta.hss;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.service.accessPolicy.AccessPolicy;
import com.sipgate.sparta.hss.service.accessPolicy.SubscriptionStateAccessPolicy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/// `sipgate.access-policy` selects the E-UTRAN access policy implementation; there is
/// no default bean, so an application that leaves the property unset fails to wire the S6a/SWx
/// handlers that inject an [AccessPolicy].
class AccessPolicyToggleTest {

    @Nested
    @SpringBootTest(properties = "sipgate.access-policy=allow-all")
    class AllowAll {

        @Autowired
        private ApplicationContext context;

        @Test
        void theAllowAllPolicyIsWired() {
            assertThat(context.getBeanNamesForType(AccessPolicy.class)).hasSize(1);
            assertThat(context.getBean(AccessPolicy.class))
                .isNotInstanceOf(SubscriptionStateAccessPolicy.class);
        }
    }

    @Nested
    @SpringBootTest(properties = "sipgate.access-policy=subscription-state")
    class SubscriptionState {

        @Autowired
        private ApplicationContext context;

        @Test
        void theSubscriptionStatePolicyIsWired() {
            assertThat(context.getBeanNamesForType(AccessPolicy.class)).hasSize(1);
            assertThat(context.getBean(AccessPolicy.class))
                .isInstanceOf(SubscriptionStateAccessPolicy.class);
        }
    }
}
