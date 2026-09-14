package com.sipgate.sparta.hss.diameter.swx.sar;

import com.sipgate.sparta.diameter.base.core.avp.AVP;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/// Loads the HSS subscription-data XML profiles and caches each as the children of the SWx
/// Non-3GPP-User-Data grouped AVP (via [Non3gppUserDataEncoder]). Mirrors the S6a
/// SubscriptionDataFactory. No APN-Configuration validation is performed (like S6a) — override/
/// template profiles legitimately carry none, and validating every loaded profile would break context start.
///
/// [#init()] must be called once after construction, before any other method.
public class Non3gppUserDataFactory {

    public static final String DEFAULT_PROFILE_NAME = "default";
    private static final String PROFILE_FILE_EXT = ".xml";

    private final Path profileDir;
    private final MeterRegistry meterRegistry;
    private final Non3gppUserDataEncoder encoder;
    private Map<String, List<AVP>> profileToAvps = null;

    public Non3gppUserDataFactory(
        final String profileDir,
        final MeterRegistry meterRegistry,
        final Non3gppUserDataEncoder encoder)
    {
        this.profileDir = Paths.get(profileDir);
        this.meterRegistry = meterRegistry;
        this.encoder = encoder;
    }

    public void init() {
        if (!Files.exists(profileDir)) {
            throw new IllegalStateException("'sipgate.profileDir' does not exist: " + profileDir);
        }
        try (final var stream = Files.walk(profileDir, 2)) {
            profileToAvps = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(PROFILE_FILE_EXT))
                .collect(Collectors.toMap(
                    p -> p.toString().replace(profileDir + "/", "").replace(PROFILE_FILE_EXT, ""),
                    p -> encoder.encode(readXml(p))));
        } catch (final IOException e) {
            throw new IllegalStateException("Error reading xmls", e);
        }
        if (!profileToAvps.containsKey(DEFAULT_PROFILE_NAME)) {
            throw new IllegalStateException("Required profile is missing: " + DEFAULT_PROFILE_NAME);
        }
    }

    /// @param profileName base profile name (without `.xml`)
    /// @param msisdn      reserved for a future Subscription-Id (443) — currently ignored (see class doc)
    public synchronized List<AVP> createNon3gppUserData(final String profileName, final String msisdn) {
        final var cached = profileToAvps.get(profileName);
        if (cached == null) {
            throw new IllegalArgumentException("No profile found for: " + profileName);
        }
        meterRegistry.counter("non_3gpp_user_data", "profile", profileName).increment();
        return List.copyOf(cached);
    }

    private static byte[] readXml(final Path filePath) {
        try {
            return Files.readAllBytes(filePath);
        } catch (final IOException e) {
            throw new RuntimeException("Error reading xml: " + filePath, e);
        }
    }
}
