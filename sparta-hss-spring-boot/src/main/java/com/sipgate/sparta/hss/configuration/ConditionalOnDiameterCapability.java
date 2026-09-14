package com.sipgate.sparta.hss.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/// Matches when the given Diameter application is listed in `sipgate.diameter.capabilities`
/// (case-insensitive; the [com.sipgate.sparta.hss.diameter.client.DiameterConfig] default applies
/// when the property is absent).
///
/// The same list drives the applications advertised in the CER, so what the HSS advertises and
/// which handlers it actually serves cannot diverge.
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnDiameterCapabilityCondition.class)
public @interface ConditionalOnDiameterCapability {

    /// The capability token, e.g. `"SWx"` or `"Cx/Dx"`.
    String value();
}
