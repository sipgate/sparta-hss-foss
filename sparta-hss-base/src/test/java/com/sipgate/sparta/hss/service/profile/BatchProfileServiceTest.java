package com.sipgate.sparta.hss.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.hss.persistence.EffectiveProfileDao;
import com.sipgate.sparta.hss.persistence.EffectiveProfileDao.UpdateProfileResult;
import com.sipgate.sparta.hss.persistence.ImsiProfileDao;
import com.sipgate.sparta.hss.persistence.TacProfileDao;
import java.util.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchProfileServiceTest {
    public static final String NEW_PROFILE = "new-profile";

    public static final String IMSI = "imsi";
    public static final String TAC = "tac";
    public static final String ANOTHERIMSI = "anotherimsi";
    public static final String SOME_PROFILE = "some-profile";
    public static final String ANOTHER_TAC = "another-tac";


    @InjectMocks
    private BatchProfileService underTest;

    @Mock
    private ImsiProfileDao imsiProfileDao;

    @Mock
    private TacProfileDao tacProfileDao;

    @Mock
    private ProfileService profileService;

    @Mock
    private EffectiveProfileDao effectiveProfileRepository;


    @Captor
    private ArgumentCaptor<Collection<UpdateProfileResult>> profileServiceArgumentCaptor;


    @Nested
    class ImsiBatch {
        @Test
        public void itHandlesNewStateWithAnEmptyDatabase() throws InterruptedException {
            // GIVEN
            when(imsiProfileDao.findAll()).thenReturn(Collections.emptyMap());

            when(effectiveProfileRepository.getUpdateProfileResults(anySet(), anySet()))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            "default",
                            "vplmnid")))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            NEW_PROFILE,
                            "vplmnid")));

            doNothing().when(profileService).updateProfiles(profileServiceArgumentCaptor.capture());

            // WHEN
            final Map<String, String> newState = new HashMap<>();
            newState.put(IMSI, NEW_PROFILE);
            underTest.applyNewImsiState(newState);

            // THEN
            assertThat(profileServiceArgumentCaptor.getValue())
                    .usingRecursiveComparison()
                    .isEqualTo(Collections.singletonList(
                            new UpdateProfileResult(
                                    IMSI,
                                    "mmeHost",
                                    "mmeRealm",
                                    "msisdn",
                                    NEW_PROFILE,
                                    "vplmnid")));
            verify(imsiProfileDao).saveOrUpdate(IMSI, NEW_PROFILE);
        }

        @Test
        public void itHandlesNewStateWithAPreviousImsiProfile() throws InterruptedException {
            // GIVEN
            when(imsiProfileDao.findAll()).thenReturn(Collections.singletonMap(IMSI, "old-profile"));

            when(effectiveProfileRepository.getUpdateProfileResults(anySet(), anySet()))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            "old-profile",
                            "vplmnid")))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            NEW_PROFILE,
                            "vplmnid")));

            doNothing().when(profileService).updateProfiles(profileServiceArgumentCaptor.capture());

            // WHEN
            final Map<String, String> newState = new HashMap<>();
            newState.put(IMSI, NEW_PROFILE);
            underTest.applyNewImsiState(newState);

            // THEN
            assertThat(profileServiceArgumentCaptor.getValue())
                    .usingRecursiveComparison()
                    .isEqualTo(Collections.singletonList(
                            new UpdateProfileResult(
                                    IMSI,
                                    "mmeHost",
                                    "mmeRealm",
                                    "msisdn",
                                    NEW_PROFILE,
                                    "vplmnid")));
            verify(imsiProfileDao).saveOrUpdate(IMSI, NEW_PROFILE);
        }

        @Test
        public void itHandlesNewStateWithAnOldAndEqualImsiProfile() throws InterruptedException {
            // GIVEN
            when(imsiProfileDao.findAll()).thenReturn(Collections.singletonMap(IMSI, NEW_PROFILE));

            when(effectiveProfileRepository.getUpdateProfileResults(anySet(), anySet()))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            NEW_PROFILE,
                            "vplmnid")));

            doNothing().when(profileService).updateProfiles(profileServiceArgumentCaptor.capture());

            // WHEN
            final Map<String, String> newState = new HashMap<>();
            newState.put(IMSI, NEW_PROFILE);
            underTest.applyNewImsiState(newState);

            // THEN
            assertThat(profileServiceArgumentCaptor.getValue()).hasSize(0);
            verifyNoMoreInteractions(imsiProfileDao);
        }

        @Test
        public void itHandlesEmptyNewStateByDeletingOldImsiProfile() throws InterruptedException {
            // GIVEN
            when(imsiProfileDao.findAll()).thenReturn(Collections.singletonMap(IMSI, NEW_PROFILE));

            when(effectiveProfileRepository.getUpdateProfileResults(anySet(), anySet()))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            NEW_PROFILE,
                            "vplmnid")))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            "default",
                            "vplmnid")));

            doNothing().when(profileService).updateProfiles(profileServiceArgumentCaptor.capture());

            // WHEN
            final Map<String, String> newState = new HashMap<>();
            underTest.applyNewImsiState(newState);

            // THEN
            assertThat(profileServiceArgumentCaptor.getValue()).hasSize(1);
            verify(imsiProfileDao).delete(IMSI);
        }

        @Test
        public void itDeletesOldImsiProfileAndPersistsNewOne() throws InterruptedException {
            // GIVEN
            when(imsiProfileDao.findAll()).thenReturn(Collections.singletonMap(IMSI, NEW_PROFILE));

            when(effectiveProfileRepository.getUpdateProfileResults(anySet(), anySet()))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            NEW_PROFILE,
                            "vplmnid")))
                    .thenReturn(Arrays.asList(new UpdateProfileResult(
                                    IMSI,
                                    "mmeHost",
                                    "mmeRealm",
                                    "msisdn",
                                    "default",
                                    "vplmnid"),
                            new UpdateProfileResult(
                                    ANOTHERIMSI,
                                    "mmeHost",
                                    "mmeRealm",
                                    "msisdn",
                                    SOME_PROFILE,
                                    "vplmnid")));

            doNothing().when(profileService).updateProfiles(profileServiceArgumentCaptor.capture());

            // WHEN
            final Map<String, String> newState = new HashMap<>();
            newState.put(ANOTHERIMSI, SOME_PROFILE);
            underTest.applyNewImsiState(newState);

            // THEN
            assertThat(profileServiceArgumentCaptor.getValue()).hasSize(2);
            verify(imsiProfileDao).delete(IMSI);
            verify(imsiProfileDao).saveOrUpdate(ANOTHERIMSI, SOME_PROFILE);
        }
    }

    @Nested
    class TacBatch {
        @Test
        public void itHandlesNewStateWithAnEmptyDatabase() throws InterruptedException {
            // GIVEN
            when(imsiProfileDao.findAll()).thenReturn(Collections.emptyMap());
            when(tacProfileDao.findAll()).thenReturn(Collections.emptyMap());

            when(effectiveProfileRepository.getUpdateProfileResults(anySet(), anySet()))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            "default",
                            "vplmnid")))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            NEW_PROFILE,
                            "vplmnid")));

            doNothing().when(profileService).updateProfiles(profileServiceArgumentCaptor.capture());

            // WHEN
            final Map<String, String> newState = new HashMap<>();
            newState.put(TAC, NEW_PROFILE);
            underTest.applyNewTacState(newState);

            // THEN
            assertThat(profileServiceArgumentCaptor.getValue())
                    .usingRecursiveComparison()
                    .isEqualTo(Collections.singletonList(
                            new UpdateProfileResult(
                                    IMSI,
                                    "mmeHost",
                                    "mmeRealm",
                                    "msisdn",
                                    NEW_PROFILE,
                                    "vplmnid")));
            verify(tacProfileDao).saveOrUpdate(TAC, NEW_PROFILE);
        }

        @Test
        public void itHandlesNewStateWithAPreviousTacProfile() throws InterruptedException {
            // GIVEN
            when(imsiProfileDao.findAll()).thenReturn(Collections.emptyMap());
            when(tacProfileDao.findAll()).thenReturn(Collections.singletonMap(TAC, "old-profile"));

            when(effectiveProfileRepository.getUpdateProfileResults(anySet(), anySet()))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            "old-profile",
                            "vplmnid")))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            NEW_PROFILE,
                            "vplmnid")));

            doNothing().when(profileService).updateProfiles(profileServiceArgumentCaptor.capture());

            // WHEN
            final Map<String, String> newState = new HashMap<>();
            newState.put(TAC, NEW_PROFILE);
            underTest.applyNewTacState(newState);

            // THEN
            assertThat(profileServiceArgumentCaptor.getValue())
                    .usingRecursiveComparison()
                    .isEqualTo(Collections.singletonList(
                            new UpdateProfileResult(
                                    IMSI,
                                    "mmeHost",
                                    "mmeRealm",
                                    "msisdn",
                                    NEW_PROFILE,
                                    "vplmnid")));
            verify(tacProfileDao).saveOrUpdate(TAC, NEW_PROFILE);
        }

        @Test
        public void itHandlesNewStateWithAOldAndEqualTacProfile() throws InterruptedException {
            // GIVEN
            when(imsiProfileDao.findAll()).thenReturn(Collections.emptyMap());
            when(tacProfileDao.findAll()).thenReturn(Collections.singletonMap(TAC, NEW_PROFILE));

            when(effectiveProfileRepository.getUpdateProfileResults(anySet(), anySet()))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            NEW_PROFILE,
                            "vplmnid")));

            doNothing().when(profileService).updateProfiles(profileServiceArgumentCaptor.capture());

            // WHEN
            final Map<String, String> newState = new HashMap<>();
            newState.put(TAC, NEW_PROFILE);
            underTest.applyNewTacState(newState);

            // THEN
            assertThat(profileServiceArgumentCaptor.getValue()).hasSize(0);
            verifyNoMoreInteractions(tacProfileDao);
        }

        @Test
        public void itRespectsThatTheresAnImsiProfileButUpdatesTheTacProfile() throws InterruptedException {
            // GIVEN
            when(imsiProfileDao.findAll()).thenReturn(Collections.singletonMap(IMSI, "imsi-specific-profile"));
            when(tacProfileDao.findAll()).thenReturn(Collections.singletonMap(TAC, "old-profile"));

            when(effectiveProfileRepository.getUpdateProfileResults(anySet(), anySet()))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            "imsi-specific-profile",
                            "vplmnid")));

            doNothing().when(profileService).updateProfiles(profileServiceArgumentCaptor.capture());

            // WHEN
            final Map<String, String> newState = new HashMap<>();
            newState.put(TAC, NEW_PROFILE);
            underTest.applyNewTacState(newState);

            // THEN
            assertThat(profileServiceArgumentCaptor.getValue()).hasSize(0);
            verify(tacProfileDao).saveOrUpdate(TAC, NEW_PROFILE);
        }

        @Test
        public void itRespectsThatTheresAnImsiProfileButSavesTheTacProfile() throws InterruptedException {
            // GIVEN
            when(imsiProfileDao.findAll()).thenReturn(Collections.singletonMap(IMSI, "imsi-specific-profile"));
            when(tacProfileDao.findAll()).thenReturn(Collections.emptyMap());

            when(effectiveProfileRepository.getUpdateProfileResults(anySet(), anySet()))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            "imsi-specific-profile",
                            "vplmnid")));

            doNothing().when(profileService).updateProfiles(profileServiceArgumentCaptor.capture());

            // WHEN
            final Map<String, String> newState = new HashMap<>();
            newState.put(TAC, NEW_PROFILE);
            underTest.applyNewTacState(newState);

            // THEN
            assertThat(profileServiceArgumentCaptor.getValue()).hasSize(0);
            verify(tacProfileDao).saveOrUpdate(TAC, NEW_PROFILE);
        }

        @Test
        public void itHandlesEmptyNewStateByDeletingOldTacProfile() throws InterruptedException {
            // GIVEN
            when(imsiProfileDao.findAll()).thenReturn(Collections.emptyMap());
            when(tacProfileDao.findAll()).thenReturn(Collections.singletonMap(TAC, "old-profile"));

            when(effectiveProfileRepository.getUpdateProfileResults(anySet(), anySet()))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            NEW_PROFILE,
                            "vplmnid")))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            "default",
                            "vplmnid")));

            doNothing().when(profileService).updateProfiles(profileServiceArgumentCaptor.capture());

            // WHEN
            final Map<String, String> newState = new HashMap<>();
            underTest.applyNewTacState(newState);

            // THEN
            assertThat(profileServiceArgumentCaptor.getValue()).hasSize(1);
            verify(tacProfileDao).delete(TAC);
        }

        @Test
        public void itDeletesOldTacProfileAndPersistsNewOne() throws InterruptedException {
            // GIVEN
            when(imsiProfileDao.findAll()).thenReturn(Collections.emptyMap());
            when(tacProfileDao.findAll()).thenReturn(Collections.singletonMap(TAC, "old-profile"));

            when(effectiveProfileRepository.getUpdateProfileResults(anySet(), anySet()))
                    .thenReturn(Collections.singletonList(new UpdateProfileResult(
                            IMSI,
                            "mmeHost",
                            "mmeRealm",
                            "msisdn",
                            "old-profile",
                            "vplmnid")))
                    .thenReturn(Arrays.asList(new UpdateProfileResult(
                                    IMSI,
                                    "mmeHost",
                                    "mmeRealm",
                                    "msisdn",
                                    "default",
                                    "vplmnid"),
                            new UpdateProfileResult(
                                    ANOTHERIMSI,
                                    "mmeHost",
                                    "mmeRealm",
                                    "msisdn",
                                    SOME_PROFILE,
                                    "vplmnid")));

            doNothing().when(profileService).updateProfiles(profileServiceArgumentCaptor.capture());

            // WHEN
            final Map<String, String> newState = new HashMap<>();
            newState.put(ANOTHER_TAC, SOME_PROFILE);
            underTest.applyNewTacState(newState);

            // THEN
            assertThat(profileServiceArgumentCaptor.getValue()).hasSize(2);
            verify(tacProfileDao).delete(TAC);
            verify(tacProfileDao).saveOrUpdate(ANOTHER_TAC, SOME_PROFILE);
        }
    }

}
