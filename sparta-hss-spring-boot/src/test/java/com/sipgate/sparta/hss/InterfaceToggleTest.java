package com.sipgate.sparta.hss;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.diameter.client.DiameterConnectionHandler;
import com.sipgate.sparta.hss.diameter.cx.mar.MultimediaAuthHandler;
import com.sipgate.sparta.hss.diameter.cx.sar.service.ImsService;
import com.sipgate.sparta.hss.diameter.s6a.ulr.UpdateLocationHandler;
import com.sipgate.sparta.hss.diameter.swx.mar.SwxMultimediaAuthHandler;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataFactory;
import com.sipgate.sparta.hss.diameter.swx.sar.Non3gppUserDataFactory;
import com.sipgate.sparta.hss.http.profile.BatchProfileResource;
import com.sipgate.sparta.hss.http.roaming.BlockRoamingResource;
import com.sipgate.sparta.hss.http.volte.location.LocationResource;
import com.sipgate.sparta.hss.service.health.metrics.RoamingLocationMetrics;
import com.sipgate.sparta.hss.service.profile.BatchProfileService;
import com.sipgate.sparta.hss.service.profile.ProfileService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/// The interfaces the HSS serves follow `sipgate.diameter.capabilities`: an application that is
/// not advertised in the CER gets no handler beans either.
class InterfaceToggleTest {

    @Nested
    @SpringBootTest(properties = "sipgate.diameter.capabilities=Cx/Dx,S6a/S6d")
    class WithoutSwx {

        @Autowired
        private ApplicationContext context;

        @Test
        void swxBeansAreAbsentButTheRestBoots() {
            assertThat(context.getBeanNamesForType(SwxMultimediaAuthHandler.class)).isEmpty();
            assertThat(context.getBeanNamesForType(Non3gppUserDataFactory.class)).isEmpty();
            assertThat(context.getBeanNamesForType(UpdateLocationHandler.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(ImsService.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(DiameterConnectionHandler.class)).hasSize(1);
        }
    }

    @Nested
    @SpringBootTest(properties = {
        "sipgate.diameter.capabilities=Cx/Dx,SWx",
        "sipgate.http.profile.enabled=true",
        "sipgate.http.roaming-block.enabled=true",
        "sipgate.http.volte-location.enabled=true",
    })
    class WithoutS6a {

        @Autowired
        private ApplicationContext context;

        @Test
        void s6aBeansAndTheProfileMachineryAreAbsent() {
            assertThat(context.getBeanNamesForType(UpdateLocationHandler.class)).isEmpty();
            assertThat(context.getBeanNamesForType(SubscriptionDataFactory.class)).isEmpty();
            assertThat(context.getBeanNamesForType(RoamingLocationMetrics.class)).isEmpty();
            assertThat(context.getBeanNamesForType(ProfileService.class)).isEmpty();
            assertThat(context.getBeanNamesForType(BatchProfileService.class)).isEmpty();
            // the profile API feeds S6a subscription data; without S6a it stays off even when enabled
            assertThat(context.getBeanNamesForType(BatchProfileResource.class)).isEmpty();
            assertThat(context.getBeanNamesForType(BlockRoamingResource.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(LocationResource.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(DiameterConnectionHandler.class)).hasSize(1);
        }
    }

    @Nested
    @SpringBootTest(properties = "sipgate.diameter.capabilities=S6a/S6d")
    class S6aOnly {

        @Autowired
        private ApplicationContext context;

        @Test
        void onlyS6aBeansExist() {
            assertThat(context.getBeanNamesForType(MultimediaAuthHandler.class)).isEmpty();
            assertThat(context.getBeanNamesForType(ImsService.class)).isEmpty();
            assertThat(context.getBeanNamesForType(SwxMultimediaAuthHandler.class)).isEmpty();
            assertThat(context.getBeanNamesForType(Non3gppUserDataFactory.class)).isEmpty();
            assertThat(context.getBeanNamesForType(UpdateLocationHandler.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(DiameterConnectionHandler.class)).hasSize(1);
        }
    }

    @Nested
    @SpringBootTest(properties = "sipgate.diameter.capabilities=S6a/S6d,SWx")
    class WithoutCx {

        @Autowired
        private ApplicationContext context;

        @Test
        void cxBeansAreAbsentButTheRestBoots() {
            assertThat(context.getBeanNamesForType(MultimediaAuthHandler.class)).isEmpty();
            assertThat(context.getBeanNamesForType(ImsService.class)).isEmpty();
            assertThat(context.getBeanNamesForType(SwxMultimediaAuthHandler.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(DiameterConnectionHandler.class)).hasSize(1);
        }
    }
}
