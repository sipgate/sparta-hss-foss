package com.sipgate.sparta.hss.service.health.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.hss.persistence.ImsiRange;
import com.sipgate.sparta.hss.persistence.LocationLteDao;
import com.sipgate.sparta.hss.persistence.LocationLteDao.MccMncEsimCountResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoamingLocationMetricsTest {

    private static final List<ImsiRange> ESIM_RANGES =
        List.of(new ImsiRange("999990000000100", "999990000000199"));

    @Mock
    private LocationLteDao locationLteDao;

    @Test
    void itGaugesTheCountsPerNetworkAndSimTechnology() {
        // GIVEN
        final var meterRegistry = new SimpleMeterRegistry();
        final var underTest = new RoamingLocationMetrics(meterRegistry, locationLteDao, ESIM_RANGES);
        when(locationLteDao.getMccMncEsimCounts(ESIM_RANGES))
            .thenReturn(List.of(new MccMncEsimCountResult("262", "07", 2L, 3L)));

        // WHEN
        underTest.updateRoamingLocations();

        // THEN
        verify(locationLteDao).getMccMncEsimCounts(ESIM_RANGES);
        assertThat(meterRegistry.get("roaming_location")
            .tags("sim_technology", "esim", "mcc", "262", "mnc", "07")
            .gauge().value()).isEqualTo(2.0);
        assertThat(meterRegistry.get("roaming_location")
            .tags("sim_technology", "plastic", "mcc", "262", "mnc", "07")
            .gauge().value()).isEqualTo(3.0);
    }
}
