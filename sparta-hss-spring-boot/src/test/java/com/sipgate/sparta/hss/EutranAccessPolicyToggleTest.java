package com.sipgate.sparta.hss;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.diameter.s6a.air.EutranAccessPolicy;
import com.sipgate.sparta.hss.diameter.s6a.air.SubscriptionStateAccessPolicy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/// `sipgate.eutran-access-policy` selects the E-UTRAN access policy implementation; there is
/// no default bean, so an application that leaves the property unset fails to wire the S6a/SWx
/// handlers that inject an [EutranAccessPolicy].
class EutranAccessPolicyToggleTest {

    @Nested
    @SpringBootTest(properties = "sipgate.eutran-access-policy=allow-all")
    class AllowAll {

        @Autowired
        private ApplicationContext context;

        @Test
        void theAllowAllPolicyIsWired() {
            assertThat(context.getBeanNamesForType(EutranAccessPolicy.class)).hasSize(1);
            assertThat(context.getBean(EutranAccessPolicy.class))
                .isNotInstanceOf(SubscriptionStateAccessPolicy.class);
        }
    }

    @Nested
    @SpringBootTest(properties = "sipgate.eutran-access-policy=subscription-state")
    class SubscriptionState {

        @Autowired
        private ApplicationContext context;

        @Test
        void theSubscriptionStatePolicyIsWired() {
            assertThat(context.getBeanNamesForType(EutranAccessPolicy.class)).hasSize(1);
            assertThat(context.getBean(EutranAccessPolicy.class))
                .isInstanceOf(SubscriptionStateAccessPolicy.class);
        }
    }
}
