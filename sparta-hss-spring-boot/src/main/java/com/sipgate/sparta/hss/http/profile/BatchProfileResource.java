package com.sipgate.sparta.hss.http.profile;

import static com.sipgate.sparta.hss.service.health.metrics.HttpServletDuration.record;

import com.sipgate.sparta.hss.configuration.ConditionalOnDiameterCapability;
import com.sipgate.sparta.hss.diameter.client.DiameterConfig;
import com.sipgate.sparta.hss.service.profile.BatchProfileService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnDiameterCapability(DiameterConfig.CAPABILITY_S6A_S6D)
@ConditionalOnProperty(value = "sipgate.http.profile.enabled", havingValue = "true")
@RequestMapping("/profile")
public class BatchProfileResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchProfileResource.class);

    private final BatchProfileService batchProfileService;

    private final MeterRegistry meterRegistry;

    public BatchProfileResource(final BatchProfileService batchProfileService, final MeterRegistry meterRegistry) {
        this.batchProfileService = batchProfileService;
        this.meterRegistry = meterRegistry;
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping(value = "/imsi/batch", produces = "application/json", consumes = "application/json")
    public void imsiBatch(@RequestBody final Map<String, String> newState) {
        record(meterRegistry, "POST", "/profile/imsi/batch", () -> {
            new Thread(() -> {
                try {
                    batchProfileService.applyNewImsiState(newState);
                } catch (final InterruptedException e) {
                    LOGGER.info("InterruptedException: {}", e.getMessage());
                    // ignored because DB has done its work.
                }
            }).start();
            return null;
        });
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping(value = "/tac/batch", produces = "application/json", consumes = "application/json")
    public void tacBatch(@RequestBody final Map<String, String> newState) {
        record(meterRegistry, "POST", "/profile/tac/batch", () -> {
            new Thread(() -> {
                try {
                    batchProfileService.applyNewTacState(newState);
                } catch (final InterruptedException e) {
                    LOGGER.info("InterruptedException: {}", e.getMessage());
                    // ignored because DB has done its work.
                }
            }).start();
            return null;
        });
    }
}
