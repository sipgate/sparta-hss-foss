package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.Imsi.ImsiState;
import com.sipgate.sparta.hss.persistence.entities.Imsi.ImsiType;
import com.sipgate.sparta.hss.persistence.entities.Msisdn;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MsisdnDaoTest {

  public static final String KNOWN_IMSI = "known-imsi";
  private static final String KNOWN_MSISDN = "known-msisdn";
  private MsisdnDao underTest;
  private EntityTransaction transaction;
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    final var factory = Persistence.createEntityManagerFactory("integration-test");
    entityManager = factory.createEntityManager();
    transaction = entityManager.getTransaction();

    transaction.begin();
    underTest = new MsisdnDao(entityManager);
  }

  @AfterEach
  void tearDown() {
    transaction.rollback();
  }

  @Test
  void itQueriesForKnownMsisdns() {
    // GIVEN
    final var msisdn = new Msisdn();
    msisdn.setMsisdn(KNOWN_MSISDN);
    entityManager.persist(msisdn);

    //WHEN
    final var isKnown = underTest.isMsisdnKnown(KNOWN_MSISDN);

    // THEN
    assertThat(isKnown).isTrue();
  }

  @Test
  void itQueriesMsisdnForImsis() {
    // GIVEN
    final var msisdn = new Msisdn();
    msisdn.setMsisdn(KNOWN_MSISDN);
    entityManager.persist(msisdn);

    final var sim = new Sim();
    sim.setMsisdn(msisdn);
    sim.setIccid("some-iccid");
    sim.setOp(new byte[1]);
    sim.setSecretKey(new byte[1]);
    entityManager.persist(sim);

    final var imsi = new Imsi();
    imsi.setImsi(KNOWN_IMSI);
    imsi.setType(ImsiType.main);
    imsi.setState(ImsiState.active);
    imsi.setSim(sim);
    entityManager.persist(imsi);


    //WHEN
    final var maybeMsisdn = underTest.getMsisdnByImsi(KNOWN_IMSI);

    // THEN
    assertThat(maybeMsisdn).contains(KNOWN_MSISDN);
  }

  @Test
  void unknownMsisdnsAreNotPresent() {
    // GIVEN
    final var unknownImsi = "unknown-imsi";

    // WHEN
    final var maybeMsisdn = underTest.getMsisdnByImsi(unknownImsi);

    // THEN
    assertThat(maybeMsisdn).isEmpty();
  }

  @Test
  void itDoesNotKnowUnknownMsisdns() {
    // GIVEN
    final var unknownMsisdn = "unknown-msisdn";

    // WHEN
    final var isKnown = underTest.isMsisdnKnown(unknownMsisdn);

    // THEN
    assertThat(isKnown).isFalse();
  }

}
