package com.sipgate.sparta.hss.http.volte.location;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sipgate.sparta.hss.service.volte.location.LocationService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


@ExtendWith(MockitoExtension.class)
class LocationResourceTest {

    @Spy
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private LocationService locationService;

    private MockMvc mvc;

    @BeforeEach
    public void setUp() {
        final var underTest = new LocationResource(locationService, meterRegistry);
        mvc = MockMvcBuilders.standaloneSetup(underTest).build();
    }

    @Test
    void itReturnsLocation() throws Exception {
        // GIVEN
        final var msisdn = "9999990214857";
        final var location = "sip:example.org";
        final var mcc = "262";
        final var mnc = "03";
        final var visitedPlmnId = mcc + mnc;

        when(locationService.getSipLocation(msisdn)).thenReturn(Optional.of(location));
        when(locationService.getVisitedPlmnId(msisdn)).thenReturn(Optional.of(visitedPlmnId));

        // WHEN
        final var resultActions = mvc.perform(get("/volte-location/{msisdn}", msisdn));

        // THEN
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value(location))
                .andExpect(jsonPath("$.mcc").value(mcc))
                .andExpect(jsonPath("$.mnc").value(mnc));
    }

    @Test
    void itReturnsNotFound() throws Exception {
        // GIVEN
        final var msisdn = "9999990214857";

        when(locationService.getSipLocation(msisdn)).thenReturn(Optional.empty());
        when(locationService.getVisitedPlmnId(msisdn)).thenReturn(Optional.empty());

        // WHEN
        final var resultActions = mvc.perform(get("/volte-location/{msisdn}", msisdn));

        // THEN
        resultActions
                .andExpect(status().isNotFound());
    }

    @Test
    void itReturnsNotFoundWhenSipLocationIsEmptyButVisitedPlmnIdIsPresent() throws Exception {
        // GIVEN
        final var msisdn = "9999990214857";
        final var visitedPlmnId = "26201";

        when(locationService.getSipLocation(msisdn)).thenReturn(Optional.empty());
        when(locationService.getVisitedPlmnId(msisdn)).thenReturn(Optional.of(visitedPlmnId));

        // WHEN
        final var resultActions = mvc.perform(get("/volte-location/{msisdn}", msisdn));

        // THEN
        resultActions
                .andExpect(status().isNotFound());
    }

    @Test
    void itReturnsGermanyWhenSipLocationIsPresentButVisitedPlmnIdIsEmpty() throws Exception {
        // GIVEN
        final var msisdn = "9999990214857";
        final var location = "sip:example.org";

        when(locationService.getSipLocation(msisdn)).thenReturn(Optional.of(location));
        when(locationService.getVisitedPlmnId(msisdn)).thenReturn(Optional.empty());

        // WHEN
        final var resultActions = mvc.perform(get("/volte-location/{msisdn}", msisdn));

        // THEN
        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.location").value(location))
            .andExpect(jsonPath("$.mcc").value("262"))
            .andExpect(jsonPath("$.mnc").value("22"));
    }
}