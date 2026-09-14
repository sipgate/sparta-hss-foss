package com.sipgate.sparta.hss.diameter.s6a.common;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.hss.diameter.common.Tbcd;
import com.sipgate.sparta.hss.diameter.common.auth.MncMccFormatter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/// Loads the HSS subscription-data XML profiles from disk and turns the effective profile for a
/// given VPLMN into the Subscription-Data AVP children (via [SubscriptionDataEncoder]).
///
/// [#init()] must be called once after construction, before any other method.
public class SubscriptionDataFactory {

  public static final String DEFAULT_PROFILE_NAME = "default";
  private static final String PROFILE_FILE_EXT = ".xml";

  private final Path profileDir;
  private final MeterRegistry meterRegistry;
  private final SubscriptionDataEncoder encoder;
  private Map<String, List<AVP>> profileToAvps = null;

  public SubscriptionDataFactory(
      final String profileDir,
      final MeterRegistry meterRegistry,
      final SubscriptionDataEncoder encoder) {
    this.profileDir = Paths.get(profileDir);
    this.meterRegistry = meterRegistry;
    this.encoder = encoder;
  }

  private static byte[] readXml(final Path filePath) {
    try {
      return Files.readAllBytes(filePath);
    } catch (final IOException e) {
      throw new RuntimeException("Error reading xml: " + filePath, e);
    }
  }

  public void init() {
    final var pathExists = Files.exists(profileDir);
    if (!pathExists) {
      throw new IllegalStateException("'sipgate.profileDir' does not exist: " + profileDir);
    }
    try (final var stream = Files.walk(profileDir, 2)) {
      profileToAvps =
          stream
              .filter(Files::isRegularFile)
              .filter(filePath -> filePath.toString().endsWith(PROFILE_FILE_EXT))
              .collect(
                  Collectors.toMap(
                      filePath -> filePath.toString().replace(profileDir + "/", "")
                          .replace(PROFILE_FILE_EXT, ""),
                      filePath -> encoder.encode(readXml(filePath))));
    } catch (final IOException e) {
      throw new IllegalStateException("Error reading xmls", e);
    }
    if (!profileToAvps.containsKey(DEFAULT_PROFILE_NAME)) {
      throw new IllegalStateException("Required profile is missing: " + DEFAULT_PROFILE_NAME);
    }
  }

  /// Builds the Subscription-Data AVP children for the effective profile of `vplmnId`.
  ///
  /// @param vplmnId     the visited PLMN (MCC+MNC digit string) used to pick a PLMN-specific override
  /// @param profileName the base profile name (without `.xml`)
  /// @param msisdn      the subscriber MSISDN, emitted in the Subscription-Data
  public synchronized List<AVP> createSubscriptionData(final String vplmnId, final String profileName, final String msisdn) {
    final var effectiveProfile = getEffectiveProfile(vplmnId, profileName);
    final var cachedAvps = profileToAvps.get(effectiveProfile);

    if (cachedAvps == null) {
      throw new IllegalArgumentException("No profile found for: " + effectiveProfile);
    }

    meterRegistry.counter("subscription_data", "profile", effectiveProfile, "vplmnId", vplmnId).increment();
    
    final List<AVP> result = new ArrayList<>(cachedAvps.size() + 1);
    if (msisdn != null) {
        final byte[] value = Tbcd.encodeTbcd(msisdn);
        result.add(AVP.create(new AVPKey(_3gppConstants.AVP_MSISDN, _3gppConstants.VENDOR_ID_3GPP), value));
    }

    result.addAll(cachedAvps);
    return result;
  }

  public String getEffectiveProfile(final String vplmnId, final String profileName) {
    if (vplmnId == null || vplmnId.isBlank()) {
      return profileName;
    }

    final var vplmnIdPath = vplmnId + "/" + profileName;
    if (profileToAvps.containsKey(vplmnIdPath)) {
      return vplmnIdPath;
    }

    final var mcc = MncMccFormatter.plmnToMcc(vplmnId);
    final var mccPath = mcc + "/" + profileName;
    if (profileToAvps.containsKey(mccPath)) {
      return mccPath;
    }

    return profileName;
  }
}
