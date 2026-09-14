package com.sipgate.sparta.hss.diameter.s6a.ulr;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_APN_CONFIGURATION;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.AVP_APN_CONFIGURATION_PROFILE;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.EXP_RES_DIAMETER_ERROR_USER_UNKNOWN;
import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;
import static com.sipgate.sparta.diameter._3gpp.cxdx.CxDxConstants.EXP_RES_DIAMETER_ERROR_ROAMING_NOT_ALLOWED;
import static com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants.AVP_SUBSCRIPTION_DATA;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_EXPERIMENTAL_RESULT_CODE;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.AVP_VENDOR_ID;
import static com.sipgate.sparta.diameter.base.core.DiameterConstants.RES_DIAMETER_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.diameter._3gpp.common._3gppConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.S6aConstants;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.CancelLocationRequest;
import com.sipgate.sparta.diameter._3gpp.s6a.messages.UpdateLocationRequest;
import com.sipgate.sparta.diameter.base.core.Command;
import com.sipgate.sparta.diameter.base.core.EndToEndId;
import com.sipgate.sparta.diameter.base.core.HopByHopId;
import com.sipgate.sparta.diameter.base.core.avp.AVPContainer;
import com.sipgate.sparta.diameter.base.core.Answer;
import com.sipgate.sparta.diameter.base.core.DiameterConstants;
import com.sipgate.sparta.diameter.base.core.avp.AVP;
import com.sipgate.sparta.diameter.base.core.avp.AVPKey;
import com.sipgate.sparta.diameter.base.core.avp.GroupedAVP;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;
import com.sipgate.sparta.hss.diameter.client.DiameterSessions;
import com.sipgate.sparta.hss.diameter.common.Factory;
import com.sipgate.sparta.hss.diameter.s6a.clr.CancelLocationEvent;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataEncoder;
import com.sipgate.sparta.hss.diameter.s6a.common.SubscriptionDataFactory;
import com.sipgate.sparta.hss.persistence.entities.Msisdn;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import com.sipgate.sparta.hss.service.roaming.BlockRoamingService;
import com.sipgate.sparta.hss.service.roaming.EmergencyRoamingRestriction;
import com.sipgate.sparta.hss.persistence.ImsiProfileDao;
import com.sipgate.sparta.hss.persistence.LocationLteDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.TacProfileDao;
import com.sipgate.sparta.hss.persistence.entities.LocationLTE;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.sipgate.sparta.hss.event.EventPublisher;

@ExtendWith(MockitoExtension.class)
class UpdateLocationHandlerTest {

  private static final byte[] VPLMN_26207 = new byte[] {0x62, (byte) 0xF2, 0x70};

  @Mock private SimDao simDao;
  @Mock private LocationLteDao locationLteDao;
  @Mock private ImsiProfileDao imsiProfileDao;
  @Mock private TacProfileDao tacProfileDao;
  @Mock private SubscriptionDataFactory subscriptionDataFactory;
  @Mock private EventPublisher eventPublisher;
  @Mock private BlockRoamingService blockRoamingService;
  @Mock private EmergencyRoamingRestriction emergencyRoamingRestriction;

  private UpdateLocationHandler underTest;

  @BeforeEach
  void setUp() {
    final var diameterSessions = new DiameterSessions(Factory.nodeConfig("origin-host", "origin-realm"));
    underTest = new UpdateLocationHandler(
        simDao, locationLteDao, imsiProfileDao, tacProfileDao, subscriptionDataFactory,
        new SimpleMeterRegistry(), eventPublisher, blockRoamingService, emergencyRoamingRestriction,
        diameterSessions);
  }

  private static UpdateLocationRequest.In request() {
    return request(true);
  }

  private static UpdateLocationRequest.In request(final boolean withTerminalInformation) {
    final var request = new UpdateLocationRequest.Out();
    request.setOriginHost("mme.example.org");
    request.setOriginRealm("epc.mnc007.mcc262.3gppnetwork.org");
    request.setUserName("999990000104857");
    request.setVisitedPlmnId(VPLMN_26207);
    request.setUlrFlags(1L << 1); // see documentation in UpdateLocationHandler.isS6aRequest
    if (withTerminalInformation) {
      request.setTerminalInformation(List.of(AVP.create(new AVPKey(S6aConstants.AVP_IMEI, _3gppConstants.VENDOR_ID_3GPP), "1234567809")));
    }
    try {
      final var bytes = new ByteArrayOutputStream();
      final var out = new DataOutputStream(bytes);
      request.writeTo(out, new HopByHopId(1), new EndToEndId(1));
      out.flush();
      return (UpdateLocationRequest.In) Command.parseMessage(ByteBuffer.wrap(bytes.toByteArray()));
    } catch (final Exception e) {
      throw new RuntimeException("failed to build incoming request", e);
    }
  }

  private Sim buildSimWithMsisdn(String msisdn) {
    Sim sim = new Sim();
    sim.setMsisdn(new Msisdn(msisdn));
    return sim;
  }

  @Test
  void it_returns_roaming_not_allowed_when_blocked() {
    // GIVEN
    when(blockRoamingService.isBlocked("999990000104857", "262", "07")).thenReturn(true);

    // WHEN
    final var answer = unwrapErrorAnswer(underTest.handle(request()));

    // THEN
    assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_ROAMING_NOT_ALLOWED);
  }

  @Test
  void it_returns_unable_to_comply_when_emergency_restriction_blocks_the_mcc() {
    // GIVEN
    when(emergencyRoamingRestriction.blocksMcc("262")).thenReturn(true);

    // WHEN
    final var answer = unwrapErrorAnswer(underTest.handle(request()));

    // THEN
    assertThat(answer.getResultCode()).isEqualTo(DiameterConstants.RES_DIAMETER_UNABLE_TO_COMPLY);
    // Must not record a permanent-block event; the emergency block is temporary.
    verify(blockRoamingService, never()).recordBlocked(anyString(), anyString());
    verify(blockRoamingService, never()).recordAllowed(anyString(), anyString());
  }

  @Test
  void it_returns_user_unknown_when_msisdn_not_found() {
    // GIVEN
    when(blockRoamingService.isBlocked(anyString(), anyString(), anyString())).thenReturn(false);
    when(simDao.getSimByImsiString(anyString())).thenReturn(Optional.empty());

    // WHEN
    final var answer = unwrapErrorAnswer(underTest.handle(request()));

    // THEN
    assertExperimentalErrorAnswer(answer, EXP_RES_DIAMETER_ERROR_USER_UNKNOWN);
  }

  private static Answer unwrapErrorAnswer(final CompletableFuture<?> future) {
    return ((DiameterErrorAnswerException) future.exceptionNow()).getAnswer();
  }

  private static void assertExperimentalErrorAnswer(final Answer answer, final long expectedResultCode) {
    assertThat(answer.getResultCode()).as("no base Result-Code for 3GPP results").isEqualTo(-1L);
    assertThat(answer.isError()).as("an Experimental-Result must not set the E-bit (RFC 6733 §7.6)").isFalse();
        final var experimentalResult = (AVPContainer) answer.findAVP(new AVPKey(DiameterConstants.AVP_EXPERIMENTAL_RESULT, 0));
    assertThat(experimentalResult).as("Experimental-Result grouped AVP").isNotNull();
    assertThat(experimentalResult.findAVP(new AVPKey(AVP_VENDOR_ID, 0)).getDataAsUnsignedInt())
        .isEqualTo(VENDOR_ID_3GPP);
    assertThat(experimentalResult.findAVP(new AVPKey(AVP_EXPERIMENTAL_RESULT_CODE, 0)).getDataAsUnsignedInt())
        .isEqualTo(expectedResultCode);
  }

  @Test
  void it_returns_subscription_data_on_success() throws Exception {
    // GIVEN
    stubHappyPath();
    final var request = request();
    when(locationLteDao.storeMmeLocation(anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(Optional.empty());
    when(blockRoamingService.isBlocked(request.getUserName(), "262", "07")).thenReturn(false);

    // WHEN
      final var answer = underTest.handle(request).get();

    // THEN
    verify(locationLteDao)
        .storeMmeLocation(eq(request.getUserName()), eq(request.getOriginHost()), eq(request.getOriginRealm()), eq("26207"), any());

    assertThat(answer.getResultCode()).isEqualTo(RES_DIAMETER_SUCCESS);
    assertThat(answer.findAVP(new AVPKey(AVP_SUBSCRIPTION_DATA, _3gppConstants.VENDOR_ID_3GPP))).isNotNull();
    assertThat(answer.getUlaFlags()).isEqualTo(0b0000_0001L);
    verify(eventPublisher).publish(new LocationUpdated("262", "07", "999990000104857", "9999990214857", Optional.of("1234567809")));
    // no old location at all -> nothing to cancel
    verify(eventPublisher, never()).publish(any(CancelLocationEvent.class));
    verify(blockRoamingService).recordAllowed(request.getUserName(), "262");
  }

  @Test
  void it_publishes_location_updated_without_imei() throws Exception {
    // GIVEN
    stubHappyPath();
    when(locationLteDao.storeMmeLocation(anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(Optional.empty());

    // WHEN
    underTest.handle(request(false)).get();

    // THEN
    verify(eventPublisher).publish(new LocationUpdated("262", "07", "999990000104857", "9999990214857", Optional.empty()));
  }

  @Test
  void it_sends_multiple_apn_configurations() throws ExecutionException, InterruptedException {
      // GIVEN
      stubHappyPath();
      when(locationLteDao.storeMmeLocation(anyString(), anyString(), anyString(), anyString(), any()))
          .thenReturn(Optional.empty());

      // use a real factory because we don't want to assemble all GroupedAVPs for the mock
      final var factory = new SubscriptionDataFactory("src/test/resources/imsiProfiles", new SimpleMeterRegistry(), new SubscriptionDataEncoder());
      factory.init();
      when(subscriptionDataFactory.createSubscriptionData(anyString(), anyString(), anyString()))
          .thenReturn(factory.createSubscriptionData("26202", "default", "my-msisdn"));

      // WHEN
      final var answer = underTest.handle(request()).get();

      // THEN
      final var subscriptionData = (GroupedAVP) answer.findAVP(new AVPKey(AVP_SUBSCRIPTION_DATA, _3gppConstants.VENDOR_ID_3GPP));
      final var profile = (GroupedAVP) subscriptionData.findAVP(new AVPKey(AVP_APN_CONFIGURATION_PROFILE, _3gppConstants.VENDOR_ID_3GPP));
      final var configurations = profile.findAVPs(new AVPKey(AVP_APN_CONFIGURATION, _3gppConstants.VENDOR_ID_3GPP));
      assertThat(configurations).hasSize(2);
  }

  @Test
  void it_sends_cancel_location_to_old_mme_when_mme_changed() throws Exception {
    // GIVEN
    stubHappyPath();
    final var oldLocation = new LocationLTE()
        .setMmeHostname("old-mme.example.org")
        .setMmeRealm("epc.mnc001.mcc208.3gppnetwork.org");
    when(locationLteDao.storeMmeLocation(anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(Optional.of(oldLocation));

    // WHEN
    underTest.handle(request()).get();

    // THEN
    final var eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher, atLeastOnce()).publish(eventCaptor.capture());
    final var clrEvent = eventCaptor.getAllValues().stream()
        .filter(CancelLocationEvent.class::isInstance)
        .map(CancelLocationEvent.class::cast)
        .findFirst()
        .orElseThrow();

    // the Session-Id is stamped by the outbound listener, not the handler, so it is unset here
    final var clr = (CancelLocationRequest.Out) clrEvent.getRequest();
    assertThat(clr.getAuthSessionState()).isEqualTo(DiameterConstants.AUTH_SESSION_STATE_NOT_MAINTAINED);
    assertThat(clr.getUserName()).isEqualTo("999990000104857");
    assertThat(clr.getCancellationType()).isEqualTo(S6aConstants.CANCELLATION_TYPE_MME_UPDATE_PROCEDURE);
    assertThat(clr.getClrFlags()).isEqualTo(1L); // S6a/S6d-Indicator bit, TS 29.272 §7.3.152
    assertThat(clr.getDestinationHost()).isEqualTo("old-mme.example.org");
    assertThat(clr.getDestinationRealm()).isEqualTo("epc.mnc001.mcc208.3gppnetwork.org");
  }

  @Test
  void it_sends_no_cancel_location_when_old_mme_location_is_equal() throws Exception {
    // GIVEN
    stubHappyPath();
    // same host+realm as the request's Origin-Host/-Realm, only differing in case
    final var oldLocation = new LocationLTE()
        .setMmeHostname("MME.example.org")
        .setMmeRealm("EPC.mnc007.mcc262.3gppnetwork.org");
    when(locationLteDao.storeMmeLocation(anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(Optional.of(oldLocation));

    // WHEN
    underTest.handle(request()).get();

    // THEN
    verify(eventPublisher, never()).publish(any(CancelLocationEvent.class));
  }

  @Test
  void it_sends_no_cancel_location_when_there_is_no_old_location() throws Exception {
    // GIVEN
    stubHappyPath();
    when(locationLteDao.storeMmeLocation(anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(Optional.empty());

    // WHEN
    underTest.handle(request()).get();

    // THEN
    verify(eventPublisher, never()).publish(any(CancelLocationEvent.class));
  }

  private void stubHappyPath() {
    when(blockRoamingService.isBlocked(anyString(), anyString(), anyString())).thenReturn(false);
    when(simDao.getSimByImsiString(anyString())).thenReturn(Optional.of(buildSimWithMsisdn("9999990214857")));
    when(imsiProfileDao.findByImsi(anyString())).thenReturn(Optional.empty());
    when(tacProfileDao.findByTac(any())).thenReturn(Optional.empty());
    when(subscriptionDataFactory.getEffectiveProfile(anyString(), anyString())).thenReturn("default");
    when(subscriptionDataFactory.createSubscriptionData(anyString(), anyString(), anyString()))
        .thenReturn(List.of(AVP.create(new AVPKey(S6aConstants.AVP_APN_OI_REPLACEMENT, _3gppConstants.VENDOR_ID_3GPP), "default")));
  }
}
