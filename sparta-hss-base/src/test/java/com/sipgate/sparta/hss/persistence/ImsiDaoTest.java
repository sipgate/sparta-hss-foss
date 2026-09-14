package com.sipgate.sparta.hss.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.Imsi.ImsiState;
import com.sipgate.sparta.hss.persistence.entities.Imsi.ImsiType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImsiDaoTest {

  private static final String KNOWN_IMSI = "known-imsi";

  private ImsiDao underTest;
  private EntityTransaction transaction;
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    final var factory = Persistence.createEntityManagerFactory("integration-test");
    entityManager = factory.createEntityManager();
    transaction = entityManager.getTransaction();

    transaction.begin();
    underTest = new ImsiDao(entityManager);
  }

  @AfterEach
  void tearDown() {
    transaction.rollback();
  }

  @Test
  void itQueriesForKnownImsis() {
    // GIVEN
    final var imsi = new Imsi();
    imsi.setImsi(KNOWN_IMSI);
    imsi.setState(ImsiState.active);
    imsi.setType(ImsiType.main);
    imsi.setAssignedAt(new Date());
    entityManager.persist(imsi);

    //WHEN
    final var isKnown = underTest.isImsiKnown(KNOWN_IMSI);

    // THEN
    assertThat(isKnown).isTrue();
  }

  @Test
  void itDoesNotKnowUnknownImsis() {
    // GIVEN
    final var unknownImsi = "unknown-imsi";

    // WHEN
    final var isKnown = underTest.isImsiKnown(unknownImsi);

    // THEN
    assertThat(isKnown).isFalse();
  }

}
