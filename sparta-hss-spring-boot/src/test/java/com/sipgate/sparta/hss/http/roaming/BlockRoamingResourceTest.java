package com.sipgate.sparta.hss.http.roaming;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sipgate.sparta.hss.http.roaming.json.RoamingRulesDto;
import com.sipgate.sparta.hss.service.roaming.BlockRoamingService;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedImsiOverride;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedImsiOverrideLte;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedLocation;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedLocationLte;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BlockRoamingResourceTest {
    @Mock
    private BlockRoamingService blockRoamingService;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private BlockRoamingResource underTest;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(underTest).build();
    }

    @Test
    void itReturnsDump() throws Exception {
        // GIVEN
        final var objectMapper = new ObjectMapper();
        final var rules = new RoamingRulesDto(List.of(), Map.of());
        final var requestJson = objectMapper.writeValueAsString(rules);

        final var dump = Map.of(
                "imsiOverrides", List.of(new RoamingBlockedImsiOverride(1, "imsi", "gtPrefix", "accepted", "reason")),
                "imsiOverridesLte", List.of(new RoamingBlockedImsiOverrideLte(1, "imsi", "mcc", "mnc", "accepted", "reason")),
                "blockedLocations", List.of(new RoamingBlockedLocation(1, "gtPrefix", "reason")),
                "blockedLocationsLte", List.of(new RoamingBlockedLocationLte(1, "mcc", "mnc", "reason"))
        );

        when(blockRoamingService.dumpAll()).thenReturn(dump);

        // WHEN
        final var resultActions = mvc.perform(
                post("/roaming/block/batch")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson));

        // THEN
        verify(blockRoamingService).replaceRules(rules.toDomain());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imsiOverrides.length()").value(1))
                .andExpect(jsonPath("$.imsiOverrides[0].id").value(1))
                .andExpect(jsonPath("$.imsiOverrides[0].imsi").value("imsi"))
                .andExpect(jsonPath("$.imsiOverrides[0].gtPrefix").value("gtPrefix"))
                .andExpect(jsonPath("$.imsiOverrides[0].roaming").value("accepted"))
                .andExpect(jsonPath("$.imsiOverrides[0].reason").value("reason"))

                .andExpect(jsonPath("$.imsiOverridesLte.length()").value(1))
                .andExpect(jsonPath("$.imsiOverridesLte[0].id").value(1))
                .andExpect(jsonPath("$.imsiOverridesLte[0].imsi").value("imsi"))
                .andExpect(jsonPath("$.imsiOverridesLte[0].mcc").value("mcc"))
                .andExpect(jsonPath("$.imsiOverridesLte[0].mnc").value("mnc"))
                .andExpect(jsonPath("$.imsiOverridesLte[0].roaming").value("accepted"))
                .andExpect(jsonPath("$.imsiOverridesLte[0].reason").value("reason"))

                .andExpect(jsonPath("$.blockedLocations.length()").value(1))
                .andExpect(jsonPath("$.blockedLocations[0].id").value(1))
                .andExpect(jsonPath("$.blockedLocations[0].gtPrefix").value("gtPrefix"))
                .andExpect(jsonPath("$.blockedLocations[0].reason").value("reason"))

                .andExpect(jsonPath("$.blockedLocationsLte.length()").value(1))
                .andExpect(jsonPath("$.blockedLocationsLte[0].id").value(1))
                .andExpect(jsonPath("$.blockedLocationsLte[0].mcc").value("mcc"))
                .andExpect(jsonPath("$.blockedLocationsLte[0].mnc").value("mnc"))
                .andExpect(jsonPath("$.blockedLocationsLte[0].reason").value("reason"))
        ;
    }
}