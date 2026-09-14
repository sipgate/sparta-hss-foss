package com.sipgate.sparta.hss;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.diameter.common.auth.AkaV1Md5;
import com.sipgate.sparta.hss.diameter.common.auth.Authenticator;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageAuthenticator;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageInput;
import com.sipgate.sparta.hss.diameter.common.auth.MilenageLogger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/// The AUC is an extension point: an application that defines its own [Authenticator] bean
/// replaces the built-in Milenage implementation.
@SpringBootTest
class AuthenticatorOverrideTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void aUserDefinedAuthenticatorReplacesTheMilenageDefault() {
        assertThat(context.getBean(Authenticator.class)).isInstanceOf(HsmAuthenticator.class);
        assertThat(context.getBeanNamesForType(MilenageAuthenticator.class)).isEmpty();
    }

    @TestConfiguration
    static class CustomAucConfiguration {

        @Bean
        Authenticator hsmAuthenticator() {
            return new HsmAuthenticator();
        }
    }

    /// Stands in for an operator-specific AUC, e.g. one whose key material lives in an HSM.
    static class HsmAuthenticator implements Authenticator {

        @Override
        public AkaV1Md5 generate4GAuthenticationVector(
            final MilenageLogger milenageLogger, final MilenageInput input, final String visitedPlmnId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AkaV1Md5 generate3GAuthenticationVector(final MilenageLogger milenageLogger, final MilenageInput input) {
            throw new UnsupportedOperationException();
        }
    }
}
