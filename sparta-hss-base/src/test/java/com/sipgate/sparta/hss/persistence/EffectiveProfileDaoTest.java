package com.sipgate.sparta.hss.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.persistence.entities.*;
import com.sipgate.sparta.hss.persistence.EffectiveProfileDao.UpdateProfileResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectiveProfileDaoTest {

  private static final String PROFILE_TAC = "tac-profile";
  private static final String PROFILE_DEFAULT = "default";
  private static final String PROFILE_IMSI = "imsi-profile";
  private EntityTransaction transaction;
  private EntityManager entityManager;
  private EffectiveProfileDao underTest;

  @BeforeEach
  void setUp() {
    final var factory = Persistence.createEntityManagerFactory("integration-test");
    entityManager = factory.createEntityManager();
    transaction = entityManager.getTransaction();

    transaction.begin();
    underTest = new EffectiveProfileDao(entityManager);
  }

  @AfterEach
  void tearDown() {
    transaction.rollback();
    entityManager.close();
  }

  @Test
  void itResolvesToDefaultProfileWhenNoProfilesAreSet() {
    prepareData(1, null, null);

    final Set<String> imsis = new HashSet<>();
    imsis.add("imsi");

    final Set<String> tacs = new HashSet<>();
    tacs.add("imsi-tac");

    final var updateProfileResults =
            underTest.getUpdateProfileResults(imsis, tacs);

    assertThat(updateProfileResults).hasSize(1);
    final var updateProfileResult = updateProfileResults.getFirst();

    assertThat(updateProfileResult)
            .usingRecursiveComparison()
            .isEqualTo(new UpdateProfileResult(
                    "imsi", "mmeHostname", "mmeRealm", "msisdn", PROFILE_DEFAULT, "vplmnid"));
  }

  @Test
  void itResolvesToTacProfileWhenTacProfileIsSet() {
    final var knownTac = "imsi-tac";
    final var tp = new TacProfile(knownTac, PROFILE_TAC, new Date());
    prepareData(1, tp, null);

    final Set<String> imsis = new HashSet<>();
    imsis.add("imsi");

    final Set<String> tacs = new HashSet<>();
    tacs.add(knownTac);

    final var updateProfileResults =
            underTest.getUpdateProfileResults(imsis, tacs);

    assertThat(updateProfileResults).hasSize(1);
    final var updateProfileResult = updateProfileResults.getFirst();

    assertThat(updateProfileResult)
            .usingRecursiveComparison()
            .isEqualTo(new UpdateProfileResult(
                    "imsi", "mmeHostname", "mmeRealm", "msisdn", PROFILE_TAC, "vplmnid"));
  }

  @Test
  void imsiProfilesArePreferredOverTacProfiles() {
    final var knownTac = "imsi-tac";
    final var tp = new TacProfile(knownTac, PROFILE_TAC, new Date());
    prepareData(1, tp, PROFILE_IMSI);

    final Set<String> imsis = new HashSet<>();
    final var knownImsi = "imsi";
    imsis.add(knownImsi);

    final Set<String> tacs = new HashSet<>();
    tacs.add(knownTac);

    final var updateProfileResults =
            underTest.getUpdateProfileResults(imsis, tacs);

    assertThat(updateProfileResults).hasSize(1);
    final var updateProfileResult = updateProfileResults.getFirst();

    assertThat(updateProfileResult)
            .usingRecursiveComparison()
            .isEqualTo(new UpdateProfileResult(
                    knownImsi, "mmeHostname", "mmeRealm", "msisdn", PROFILE_IMSI, "vplmnid"));
  }

  @Test
  void itResolvesImsiProfilWhenNoTacProfileIsSet() {
    prepareData(1, null, PROFILE_IMSI);

    final Set<String> imsis = new HashSet<>();
    final var knownImsi = "imsi";
    imsis.add(knownImsi);

    final Set<String> tacs = new HashSet<>();
    tacs.add("some-tac");

    final var updateProfileResults =
            underTest.getUpdateProfileResults(imsis, tacs);

    assertThat(updateProfileResults).hasSize(1);
    final var updateProfileResult = updateProfileResults.getFirst();

    assertThat(updateProfileResult)
            .usingRecursiveComparison()
            .isEqualTo(new UpdateProfileResult(
                    knownImsi, "mmeHostname", "mmeRealm", "msisdn", PROFILE_IMSI, "vplmnid"));
  }

  @Test
  void itWorksWithMultipleRows() {
    prepareData(2, null, null);
    final Set<String> imsis = new HashSet<>();
    imsis.add("imsi-0");
    imsis.add("imsi-1");

    final Set<String> tacs = new HashSet<>();
    tacs.add("imsi-tac");

    final var updateProfileResults =
            underTest.getUpdateProfileResults(imsis, tacs);

    assertThat(updateProfileResults).hasSize(2);
  }

  @Test
  void itReturnsAnEmptyListIfImsisAreEmpty() {
    prepareData(1, null, null);

    final Set<String> imsis = new HashSet<>();

    final Set<String> tacs = new HashSet<>();
    tacs.add("imsi-tac");

    final var updateProfileResults =
            underTest.getUpdateProfileResults(imsis, tacs);

    assertThat(updateProfileResults).isEmpty();
  }

  @Test
  void itReturnsResultsIfTacsAreEmpty() {
    prepareData(1, null, null);

    final Set<String> imsis = new HashSet<>();
    imsis.add("imsi");

    final Set<String> tacs = new HashSet<>();

    final var updateProfileResults =
            underTest.getUpdateProfileResults(imsis, tacs);

    assertThat(updateProfileResults).hasSize(1);
    final var updateProfileResult = updateProfileResults.getFirst();

    assertThat(updateProfileResult)
            .usingRecursiveComparison()
            .isEqualTo(new UpdateProfileResult(
                    "imsi", "mmeHostname", "mmeRealm", "msisdn", PROFILE_DEFAULT, "vplmnid"));
  }

  private void prepareData(final int numberOfImsis, final TacProfile tacProfile, final String imsiProfile) {
    for (var i = 0; i < numberOfImsis; i++) {
      final var suffix = numberOfImsis > 1 ? "-" + i : "";
      final var msisdn = new Msisdn("msisdn" + suffix);
      entityManager.persist(msisdn);

      final var sim = new Sim();
      sim.setMsisdn(msisdn);
      sim.setIccid("iccid" + suffix);
      sim.setOp(new byte[1]);
      sim.setSecretKey(new byte[1]);
      entityManager.persist(sim);

      final var imsi = new Imsi();
      imsi.setSim(sim);
      imsi.setImsi("imsi" + suffix);
      imsi.setState(Imsi.ImsiState.active);
      imsi.setType(Imsi.ImsiType.main);
      imsi.setAssignedAt(new Date());
      entityManager.persist(imsi);

      if (tacProfile != null) {
        entityManager.persist(tacProfile);
      }

      if (imsiProfile != null) {
        final var ip = new ImsiProfile(imsi, imsiProfile, new Date());
        entityManager.persist(ip);
      }


      final var location = new LocationLTE(imsi, "mmeHostname", "mmeRealm", "vplmnid", "imsi-tac");
      location.setLastUpdate(new Date());
      entityManager.persist(location);
    }


    entityManager.flush();
  }
}