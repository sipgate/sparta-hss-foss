package com.sipgate.sparta.hss.http.volte.location;

import static com.sipgate.sparta.hss.service.health.metrics.HttpServletDuration.record;

import com.sipgate.sparta.hss.service.volte.location.LocationService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(value = "sipgate.http.volte-location.enabled", havingValue = "true")
@RequestMapping(value = "/volte-location", produces = "application/json")
public class LocationResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocationResource.class);

    private final LocationService locationService;

    private final MeterRegistry meterRegistry;

    public LocationResource(final LocationService locationService, final MeterRegistry meterRegistry) {
        this.locationService = locationService;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/{msisdn}")
    public ResponseEntity<Map<String, String>> volteLocation(@PathVariable("msisdn") final String msisdn) {
        return record(meterRegistry, "GET", "/volte-location/{msisdn}", () -> {
            final var location = locationService.getSipLocation(msisdn);
            final var visitedPlmnId = locationService.getVisitedPlmnId(msisdn);
            if (location.isPresent()) {
                final var vlpmnId = visitedPlmnId.orElse("26222");
                final var mcc = vlpmnId.substring(0, 3);
                final var mnc = vlpmnId.substring(3);

                LOGGER.info("VoLTE Location - MSISDN: {} is at: {} in PLMN: {}", msisdn, location.get(), vlpmnId);
                return ResponseEntity.ok(
                        Map.of(
                                "location", location.get(),
                                "mnc", mnc,
                                "mcc", mcc
                        )
                );
            } else {
                LOGGER.info("VoLTE Location - MSISDN: {} not found", msisdn);
                return ResponseEntity.notFound().build();
            }
        });
    }
}
