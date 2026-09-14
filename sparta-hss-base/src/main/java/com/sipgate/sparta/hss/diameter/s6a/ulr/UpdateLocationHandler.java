package com.sipgate.sparta.hss.diameter.s6a.ulr;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.EXP_RES_DIAMETER_ERROR_ROAMING_NOT_ALLOWED;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_IMEI;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_SOFTWARE_VERSION;
import static com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataFactory.DEFAULT_PROFILE_NAME;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.CancelLocationRequest;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.UpdateLocationAnswer;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.UpdateLocationRequest;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.client.RegisterableDiameterHandler;
import com.sipgate.sparta.hss.diameter.common.auth.MncMccFormatter;
import com.sipgate.sparta.hss.diameter.s6a.clr.CancelLocationEvent;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataFactory;
import com.sipgate.sparta.hss.event.EventPublisher;
import com.sipgate.sparta.hss.service.roaming.BlockRoamingService;
import com.sipgate.sparta.hss.service.roaming.EmergencyRoamingRestriction;
import com.sipgate.sparta.hss.persistence.ImsiProfileDao;
import com.sipgate.sparta.hss.persistence.LocationLteDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.TacProfileDao;
import com.sipgate.sparta.hss.persistence.entities.LocationLTE;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateLocationHandler implements RegisterableDiameterHandler<UpdateLocationRequest.In, UpdateLocationAnswer.Out> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateLocationHandler.class);
    private final SimDao simDao;
    private final LocationLteDao locationLteDao;
    private final ImsiProfileDao imsiProfileDao;
    private final TacProfileDao tacProfileDao;

    private final SubscriptionDataFactory subscriptionDataFactory;

    private final MeterRegistry meterRegistry;
    private final EventPublisher eventPublisher;
    private final BlockRoamingService blockRoamingService;
    private final EmergencyRoamingRestriction emergencyRoamingRestriction;
    private final DiameterSessions diameterSessions;

    public UpdateLocationHandler(
        final SimDao simDao,
        final LocationLteDao locationLteDao,
        final ImsiProfileDao imsiProfileDao,
        final TacProfileDao tacProfileDao,
        final SubscriptionDataFactory subscriptionDataFactory,
        final MeterRegistry meterRegistry,
        final EventPublisher eventPublisher,
        final BlockRoamingService blockRoamingService,
        final EmergencyRoamingRestriction emergencyRoamingRestriction,
        final DiameterSessions diameterSessions) {
        this.simDao = simDao;
        this.locationLteDao = locationLteDao;
        this.imsiProfileDao = imsiProfileDao;
        this.tacProfileDao = tacProfileDao;
        this.subscriptionDataFactory = subscriptionDataFactory;
        this.meterRegistry = meterRegistry;
        this.eventPublisher = eventPublisher;
        this.blockRoamingService = blockRoamingService;
        this.emergencyRoamingRestriction = emergencyRoamingRestriction;
        this.diameterSessions = diameterSessions;
    }

    private static String extractTac(final UpdateLocationRequest request) {

        if (request.getTerminalInformation() == null) {
            return null;
        }
        final var imei = extractImei(request);
        if (imei == null) {
            return null;
        }
        if (imei.length() < 8) {
            return imei;
        }
        return imei.substring(0, 8);
    }

    private static String extractImei(final UpdateLocationRequest request) {
        if (request.getTerminalInformation() == null) {
            return null;
        }

        final var avp = request.getTerminalInformation().findAVP(new AVPKey(AVP_IMEI, VENDOR_ID_3GPP));

        if (avp == null) {
            return null;
        }

        return avp.getDataAsString();
    }

    private static String extractSoftwareVersion(final UpdateLocationRequest request) {
        if (request.getTerminalInformation() == null) {
            return null;
        }

        final var avp = request.getTerminalInformation().findAVP(new AVPKey(AVP_SOFTWARE_VERSION, VENDOR_ID_3GPP));

        if (avp == null) {
            return null;
        }

        return avp.getDataAsString();
    }

    private static boolean hasEqualMmeLocation(
        final LocationLTE locationLte,
        final String mmeHost,
        final String mmeRealm) {
        final var oldHostname = locationLte.getMmeHostname().toLowerCase();
        final var newHostname = mmeHost.toLowerCase();
        final var oldRealm = locationLte.getMmeRealm().toLowerCase();
        final var newRealm = mmeRealm.toLowerCase();
        return oldHostname.equals(newHostname) && oldRealm.equals(newRealm);
    }

    /// This bit, when set, indicates that the ULR message is sent on
    /// the S6a interface, i.e. the source node is an MME (or a
    /// combined MME/SGSN to which the UE is attached via E-
    /// UTRAN).
    /// This bit, when cleared, indicates that the ULR message is sent
    /// on the S6d interface, i.e. the source node is an SGSN (or a
    /// combined MME/SGSN to which the UE is attached via UTRAN
    /// or GERAN).
    /// - Ts 29.272 Table 7.3.7/1: ULR-Flags
    private static boolean isS6aRequest(final UpdateLocationRequest request) {
        final var flags = request.getUlrFlags();
        return (flags & 0x0002) != 0;
    }

    @Override
    public Class<UpdateLocationRequest.In> requestType() {
        return UpdateLocationRequest.In.class;
    }

    @Override
    @Transactional
    public CompletableFuture<UpdateLocationAnswer.Out> handle(final UpdateLocationRequest.In request) {
        try {
            return doHandle(request);
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<UpdateLocationAnswer.Out> doHandle(final UpdateLocationRequest.In request) throws DiameterErrorAnswerException {
        final var imsi = request.getUserName();
        final var visitedPlmn = MncMccFormatter.snIdToMccMnc(request.getVisitedPlmnId());

        LOGGER.info("imsi: {} on VisitedPlmnId: {} from mme {} ", imsi, visitedPlmn.plmn(), request.getOriginHost());

        // Emergency MCC allowlist: answer 5012 (a transient signal to the UE, EMM cause #17)
        // instead of 5004, so phones retry instead of forbidding the PLMN.
        if (emergencyRoamingRestriction.blocksMcc(visitedPlmn.mcc())) {
            meterRegistry.counter("roaming_blocked_emergency", "mcc", visitedPlmn.mcc()).increment();
            LOGGER.info("imsi: {} rejected by emergency roaming restriction (mcc {})", imsi, visitedPlmn.mcc());
            throw new DiameterErrorAnswerException(
                DiameterMessageFactory.createErrorAnswer(request, DiameterConstants.RES_DIAMETER_UNABLE_TO_COMPLY));
        }

        if (blockRoamingService.isBlocked(imsi, visitedPlmn.mcc(), visitedPlmn.mnc())) {
            blockRoamingService.recordBlocked(imsi, visitedPlmn.mcc());
            throw new DiameterErrorAnswerException(
                DiameterMessageFactory.createExperimentalResultAnswer(request, _3gppConstants.VENDOR_ID_3GPP, EXP_RES_DIAMETER_ERROR_ROAMING_NOT_ALLOWED));
        }

        blockRoamingService.recordAllowed(imsi, visitedPlmn.mcc());

        final var maybeMsisdn = getMsisdnByImsi(imsi);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("- getMsisdnByImsi {} ", maybeMsisdn.orElse("?"));
        }

        if (maybeMsisdn.isEmpty()) {
            LOGGER.debug("- msisdn not found for imsi {}", imsi);
            throw new DiameterErrorAnswerException(
                DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, _3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN));
        }
        final var msisdn = maybeMsisdn.get();

        String tac = null;  // will be overwritten if request is an "s6a"
        if (isS6aRequest(request)) {
            // If it was an "s6d" the request did not come from an MME but from an SGSN.

            tac = extractTac(request);
            LOGGER.trace("- tac {} ", tac);
            final var maybeOldLocation = locationLteDao.storeMmeLocation(
                imsi,
                request.getOriginHost(),
                request.getOriginRealm(),
                visitedPlmn.plmn(),
                tac);
            if (maybeOldLocation.isPresent() && !hasEqualMmeLocation(
                maybeOldLocation.get(),
                request.getOriginHost(),
                request.getOriginRealm())
            ) {
                final var oldLocation = maybeOldLocation.get();
                LOGGER.trace("- oldLocation {} ", oldLocation);

                sendCancelLocationRequest(imsi, oldLocation);
            } else {
                LOGGER.trace("- no old location found");
            }
        }

        final var profileAndReason = findEffectiveProfile(imsi, tac);
        final var answer = buildUpdateLocationAnswer(request, visitedPlmn.plmn(), msisdn, profileAndReason.profileName());
        LOGGER.info("imsi: {} - updateLocationAnswer built, tac: {}", imsi, tac);

        final var effectiveProfileName = subscriptionDataFactory.getEffectiveProfile(visitedPlmn.plmn(), profileAndReason.profileName());

        if (tac != null && !tac.isEmpty()) {
            LOGGER.trace("- publish UeVisited imsi:{} tac:{}", imsi, tac);
            eventPublisher.publish(new UeVisited(imsi, tac, extractSoftwareVersion(request), effectiveProfileName, profileAndReason.profileReason()));
        }

        final var imei = extractImei(request);
        eventPublisher.publish(new LocationUpdated(
            visitedPlmn.mcc(), visitedPlmn.mnc(), imsi, msisdn,
            Optional.ofNullable(imei).filter(value -> !value.isBlank())));
        return CompletableFuture.completedFuture(answer);
    }

    /// Tells the previous MME to drop its registration for the subscriber after the UE moved
    /// to a different MME (fire-and-forget).
    private void sendCancelLocationRequest(final String imsi, final LocationLTE oldLocation) {
        final var clr = diameterSessions.createRequest(
            CancelLocationRequest.Out.class,
            // Auth-Session-State is mandatory for CLR (TS 29.272 §7.2.7); the Session-Id is stamped by
            // the outbound listener from the session layer. The library adds neither for outgoing requests.
            DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED,
            oldLocation.getMmeHostname(),
            oldLocation.getMmeRealm(),
            _3gppConstants.VENDOR_ID_3GPP,
            S6aConstants.APP_ID_S6A_S6D);
        clr.setUserName(imsi);
        // CLR-Flags bit 0 = S6a/S6d-Indicator (TS 29.272 §7.3.152): set means the CLR is sent
        // on the S6a interface, i.e. the old serving node is an MME.
        clr.setClrFlags(1L);
        clr.setCancellationType(S6aConstants.CANCELLATION_TYPE_MME_UPDATE_PROCEDURE);

        // Hand the request to the outbound listener so it is sent off the inbound request thread.
        eventPublisher.publish(new CancelLocationEvent(
            clr, "imsi=" + imsi + ", oldMme=" + oldLocation.getMmeHostname()));
    }

    private UpdateLocationAnswer.Out buildUpdateLocationAnswer(
        final UpdateLocationRequest.In request,
        final String visitedPlmnId,
        final String msisdn,
        final String profileName) {
        final var subscriptionData = subscriptionDataFactory.createSubscriptionData(visitedPlmnId, profileName, msisdn);
        final var answer = DiameterMessageFactory.createAnswer(request, DiameterConstants.RES_DIAMETER_SUCCESS);
        answer.setAVP(AVP.create(new AVPKey(S6aConstants.AVP_SUBSCRIPTION_DATA, _3gppConstants.VENDOR_ID_3GPP), subscriptionData));
        answer.setUlaFlags(0b0000_0001L);
        return answer;
    }

    private Optional<String> getMsisdnByImsi(final String imsi) {
        return simDao.getSimByImsiString(imsi).map(value -> value.getMsisdn().getMsisdn());
    }

    // helper to keep findEffectiveProfile() concise.
    private ProfileWithReason logInfoAndReturn(
        final String profile, final String imsi, final String tac, final String reason) {
        meterRegistry.counter("effective_profile", "profile", profile, "reason", reason).increment();
        LOGGER.info("profile={} for IMSI={} with TAC={} [{}]", profile, imsi, tac, reason);
        return new ProfileWithReason(profile, reason);
    }

    private ProfileWithReason findEffectiveProfile(final String imsi, final String tac) {
        Objects.requireNonNull(imsi);
        final var imsiProfile = imsiProfileDao.findByImsi(imsi);
        return imsiProfile
            .map(s -> logInfoAndReturn(s, imsi, tac, "IMSI"))
            .orElseGet(() -> tacProfileDao
                .findByTac(tac)
                .map(s -> logInfoAndReturn(s, imsi, tac, "TAC"))
                .orElseGet(() -> logInfoAndReturn(DEFAULT_PROFILE_NAME, imsi, tac, "DEFAULT"))
            );
    }

    private record ProfileWithReason(String profileName, String profileReason) {
    }
}
