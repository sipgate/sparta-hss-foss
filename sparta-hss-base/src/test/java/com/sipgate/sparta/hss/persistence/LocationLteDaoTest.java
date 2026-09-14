package com.sipgate.sparta.hss.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.LocationLTE;
import com.sipgate.sparta.hss.persistence.entities.Msisdn;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocationLteDaoTest {

  private static final String ANY_IMSI = "any-imsi";
  private static final String ANY_MME_HOST = "any-mme-host";
  private static final String ANY_MME_REALM = "any-mme-realm";
  private static final String ANY_VPLMN = "any-vplmn";
  private static final String ANY_TAC = "any-tac";
  private static final String ANY_MSISDN = "any-msisdn";

  private static final String OTHER_MME_HOST = "other-mme-host";
  private static final String OTHER_MME_REALM = "other-mme-realm";
  private static final String OTHER_TAC = "othertac";

  private EntityTransaction transaction;
  private EntityManager entityManager;
  private LocationLteDao underTest;
  private Imsi imsi;

  @BeforeEach
  void setUp() {
    final var factory = Persistence.createEntityManagerFactory("integration-test");
    entityManager = factory.createEntityManager();
    transaction = entityManager.getTransaction();

    transaction.begin();

    final var msisdn = new Msisdn();
    msisdn.setMsisdn(ANY_MSISDN);
    entityManager.persist(msisdn);

    final var sim = new Sim();
    sim.setIccid("any-iccid");
    sim.setOp(new byte[1]);
    sim.setSecretKey(new byte[1]);
    sim.setSqn(1234567899654L);
    sim.setMsisdn(msisdn);
    entityManager.persist(sim);

    imsi = new Imsi();
    imsi.setImsi(ANY_IMSI);
    imsi.setState(Imsi.ImsiState.active);
    imsi.setType(Imsi.ImsiType.main);
    imsi.setAssignedAt(new Date());
    imsi.setSim(sim);
    entityManager.persist(imsi);

    underTest = new LocationLteDao(entityManager, new ImsiDao(entityManager));
  }

  @AfterEach
  void tearDown() {
    transaction.rollback();
  }

  @Test
  void itStoresNewMmeLocations() {
    // WHEN
    final var maybeOldLocation = underTest.storeMmeLocation(ANY_IMSI, ANY_MME_HOST, ANY_MME_REALM, ANY_VPLMN, ANY_TAC);

    // THEN
    assertThat(maybeOldLocation).isEmpty();

    final var actual = entityManager.createQuery("from LocationLTE", LocationLTE.class).getSingleResult();
    assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("lastUpdate") // can't verify content because it's set to new Date()
            .isEqualTo(new LocationLTE(imsi, ANY_MME_HOST, ANY_MME_REALM, ANY_VPLMN, ANY_TAC));
  }

  @Test
  void itUpdatesExistingMmeLocations() {
    // GIVEN
    underTest.storeMmeLocation(ANY_IMSI, ANY_MME_HOST, ANY_MME_REALM, ANY_VPLMN, ANY_TAC);

    // WHEN
    final var actualOldLocation = underTest.storeMmeLocation(ANY_IMSI, OTHER_MME_HOST, OTHER_MME_REALM, ANY_VPLMN, OTHER_TAC);

    // THEN
    assertThat(actualOldLocation).isNotEmpty();
    assertThat(actualOldLocation.get().getMmeHostname()).isEqualTo(ANY_MME_HOST);
    assertThat(actualOldLocation.get().getMmeRealm()).isEqualTo(ANY_MME_REALM);

    final var actual = entityManager.createQuery("from LocationLTE", LocationLTE.class).getSingleResult();
    assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("lastUpdate") // can't verify content because it's set to new Date()
            .isEqualTo(new LocationLTE(imsi, OTHER_MME_HOST, OTHER_MME_REALM, ANY_VPLMN, OTHER_TAC));
  }

  @Test
  void itDoesNotInsertLocationsForUnknownImsis() {
    // WHEN
    final var maybeOldLocation = underTest.storeMmeLocation("wrong imsi", OTHER_MME_HOST, OTHER_MME_REALM, ANY_VPLMN, null);


    // THEN
    assertThat(maybeOldLocation).isEmpty();
    assertThat(entityManager.createQuery("from LocationLTE").getResultList()).isEmpty();
  }


  @Test
  void itFindsExistingLocation() {
    // GIVEN
    underTest.storeMmeLocation(ANY_IMSI, ANY_MME_HOST, ANY_MME_REALM, ANY_VPLMN, null);

    // WHEN
    final var result = underTest.getLocation(ANY_IMSI);

    // THEN
    assertThat(result).isNotEmpty();
    assertThat(result.get().getMmeHostname()).isEqualTo(ANY_MME_HOST);
    assertThat(result.get().getMmeRealm()).isEqualTo(ANY_MME_REALM);
  }


  @Test
  void itDoesNotFindNonExistingLocation() {
    // GIVEN
    // - just to have anything in the db that could accidentally be returned:
    underTest.storeMmeLocation(ANY_IMSI, ANY_MME_HOST, ANY_MME_REALM, ANY_VPLMN, null);

    // WHEN
    final var result = underTest.getLocation("other imsi");

    // THEN
    assertThat(result).isEmpty();
  }


  @Test
  void itDeletesExistingLocation() {
    // GIVEN
    underTest.storeMmeLocation(ANY_IMSI, ANY_MME_HOST, ANY_MME_REALM, ANY_VPLMN, null);
    final var existing = underTest.getLocation(ANY_IMSI).get();

    // WHEN
    underTest.removeExistingMmeLocation(existing);

    // THEN
    assertThat(entityManager.createQuery("from LocationLTE").getResultList()).isEmpty();
  }


  // ====================


  @Test
  void itFindsExistingLocationByMsisn() {
    // GIVEN
    underTest.storeMmeLocation(ANY_IMSI, ANY_MME_HOST, ANY_MME_REALM, ANY_VPLMN, null);

    // WHEN
    final var result = underTest.getLocationByMsisdn(ANY_MSISDN);

    // THEN
    assertThat(result).isNotEmpty();
    assertThat(result.get().getMmeHostname()).isEqualTo(ANY_MME_HOST);
    assertThat(result.get().getMmeRealm()).isEqualTo(ANY_MME_REALM);
    assertThat(result.get().getVisitedPlmnId()).isEqualTo(ANY_VPLMN);
  }


  @Test
  void itDoesNotFindNonExistingLocationMyMsisdn() {
    // GIVEN
    // - just to have anything in the db that could accidentally be returned:
    underTest.storeMmeLocation(ANY_IMSI, ANY_MME_HOST, ANY_MME_REALM, ANY_VPLMN, null);

    // WHEN
    final var result = underTest.getLocationByMsisdn("other msisdn");

    // THEN
    assertThat(result).isEmpty();
  }

  @Test
  void itSplitsRoamingCountsByEsimImsiRanges() {
    // GIVEN: one subscriber inside the eSIM range, one outside, both in the same visited network
    persistImsi("999990000000150");
    persistImsi("999990000000500");
    underTest.storeMmeLocation("999990000000150", ANY_MME_HOST, ANY_MME_REALM, "99999", ANY_TAC);
    underTest.storeMmeLocation("999990000000500", ANY_MME_HOST, ANY_MME_REALM, "99999", ANY_TAC);

    // WHEN
    final var counts = underTest.getMccMncEsimCounts(
        List.of(new ImsiRange("999990000000100", "999990000000199")));

    // THEN
    assertThat(counts).contains(new LocationLteDao.MccMncEsimCountResult("999", "99", 1L, 1L));
  }

  @Test
  void itCountsEverySubscriberAsPlasticWithoutEsimRanges() {
    // GIVEN
    persistImsi("999990000000150");
    underTest.storeMmeLocation("999990000000150", ANY_MME_HOST, ANY_MME_REALM, "99999", ANY_TAC);

    // WHEN
    final var counts = underTest.getMccMncEsimCounts(List.of());

    // THEN
    assertThat(counts).contains(new LocationLteDao.MccMncEsimCountResult("999", "99", 0L, 1L));
  }

  private void persistImsi(final String imsiValue) {
    final var numericImsi = new Imsi();
    numericImsi.setImsi(imsiValue);
    numericImsi.setState(Imsi.ImsiState.active);
    numericImsi.setType(Imsi.ImsiType.main);
    numericImsi.setAssignedAt(new Date());
    numericImsi.setSim(imsi.getSim());
    entityManager.persist(numericImsi);
  }
}
