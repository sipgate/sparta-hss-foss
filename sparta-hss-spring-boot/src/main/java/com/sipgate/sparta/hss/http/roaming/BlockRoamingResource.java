package com.sipgate.sparta.hss.http.roaming;

import static com.sipgate.sparta.hss.service.health.metrics.HttpServletDuration.record;

import com.sipgate.sparta.hss.http.roaming.json.RoamingRulesDto;
import com.sipgate.sparta.hss.service.roaming.BlockRoamingService;
import com.sipgate.sparta.hss.persistence.entities.RoamingBlockedEntity;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(value = "sipgate.http.roaming-block.enabled", havingValue = "true")
@RequestMapping("/roaming/block")
public class BlockRoamingResource {

    private final BlockRoamingService blockRoamingService;

    private final MeterRegistry meterRegistry;

    public BlockRoamingResource(final BlockRoamingService blockRoamingService, final MeterRegistry meterRegistry) {
        this.blockRoamingService = blockRoamingService;
        this.meterRegistry = meterRegistry;
    }

    @ResponseStatus(HttpStatus.OK)
    @PostMapping(value = "/batch", produces = "application/json", consumes = "application/json")
    public Map<String, List<? extends RoamingBlockedEntity>> replaceRules(@RequestBody final RoamingRulesDto rules) {
        return record(meterRegistry, "POST", "/roaming/block/batch", () -> {
            blockRoamingService.replaceRules(rules.toDomain());
            return blockRoamingService.dumpAll();
        });
    }

    /// Rule validation happens in the domain records built by [RoamingRulesDto#toDomain].
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> onInvalidRules(final IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

}
