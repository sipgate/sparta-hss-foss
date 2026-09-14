package com.sipgate.sparta.hss;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.http.profile.BatchProfileResource;
import com.sipgate.sparta.hss.http.roaming.BlockRoamingResource;
import com.sipgate.sparta.hss.http.volte.location.LocationResource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/// The HTTP resources are opt-in (`sipgate.http.<resource>.enabled`) and stay off until a
/// deployment switches them on.
class HttpResourceToggleTest {

    @Nested
    @SpringBootTest
    class WithoutConfiguration {

        @Autowired
        private ApplicationContext context;

        @Test
        void noResourceExists() {
            assertThat(context.getBeanNamesForType(BatchProfileResource.class)).isEmpty();
            assertThat(context.getBeanNamesForType(BlockRoamingResource.class)).isEmpty();
            assertThat(context.getBeanNamesForType(LocationResource.class)).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(properties = {
        "sipgate.http.profile.enabled=true",
        "sipgate.http.roaming-block.enabled=true",
        "sipgate.http.volte-location.enabled=true",
    })
    class AllResourcesEnabled {

        @Autowired
        private ApplicationContext context;

        @Test
        void allResourcesExist() {
            assertThat(context.getBeanNamesForType(BatchProfileResource.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(BlockRoamingResource.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(LocationResource.class)).hasSize(1);
        }
    }
}
