package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.diameter.client.DiameterConfig;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class OnDiameterCapabilityCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
        final var wanted = (String) metadata
            .getAnnotationAttributes(ConditionalOnDiameterCapability.class.getName())
            .get("value");
        final var capabilities = Binder.get(context.getEnvironment())
            .bind("sipgate.diameter.capabilities", String[].class)
            .map(List::of)
            .orElse(DiameterConfig.DEFAULT_CAPABILITIES);

        final var message = ConditionMessage.forCondition(ConditionalOnDiameterCapability.class)
            .because("sipgate.diameter.capabilities is " + capabilities);
        return capabilities.stream().anyMatch(wanted::equalsIgnoreCase)
            ? ConditionOutcome.match(message)
            : ConditionOutcome.noMatch(message);
    }
}
