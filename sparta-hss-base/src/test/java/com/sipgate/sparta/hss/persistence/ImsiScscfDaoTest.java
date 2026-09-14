package com.sipgate.sparta.hss.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.Imsi.ImsiState;
import com.sipgate.sparta.hss.persistence.entities.Imsi.ImsiType;
import com.sipgate.sparta.hss.persistence.entities.ImsiScscf;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImsiScscfDaoTest {

  private static final String ANY_IMSI = "any-imsi";
  private static final String ANY_SCSCF = "any-scscf.example.org";
  private static final String ANY_DIAMETER_HOST = "any-diameter-host.example.org";
  private static final String ANY_DIAMETER_REALM = "any-diameter-realm.example.org";

  private static final String OTHER_SCSCF = "other-scscf";
  private static final String OTHER_DIAMETER_HOST = "other-diameter-host.example.org";
  private static final String OTHER_DIAMETER_REALM = "other-diameter-realm.example.org";

  private ImsiScscfDao underTest;
  private EntityTransaction transaction;
  private Imsi imsi;

  @BeforeEach
  void setUp() {
    final var factory = Persistence.createEntityManagerFactory("integration-test");
    final var entityManager = factory.createEntityManager();
    transaction = entityManager.getTransaction();

    transaction.begin();
    imsi = new Imsi();
    imsi.setImsi(ANY_IMSI);
    imsi.setState(ImsiState.active);
    imsi.setType(ImsiType.main);
    imsi.setAssignedAt(new Date());
    entityManager.persist(imsi);
    underTest = new ImsiScscfDao(entityManager);
  }

  @AfterEach
  void tearDown() {
    transaction.rollback();
  }

  @Test
  void itReturnsEmptyScscfInitially() {
    // GIVEN

    // WHEN
    final var maybeImsiScscf = underTest.getScscf(ANY_IMSI);

    // THEN
    assertThat(maybeImsiScscf).isEmpty();
  }

  @Test
  void itReturnsScscfWhenStored() {
    // GIVEN

    // WHEN
    underTest.setScscf(ANY_IMSI, ANY_SCSCF, ANY_DIAMETER_HOST, ANY_DIAMETER_REALM);
    final var maybeImsiScscf = underTest.getScscf(ANY_IMSI);

    // THEN
    final var expected = new ImsiScscf();
    expected.setImsi(imsi);
    expected.setScscf(ANY_SCSCF);
    expected.setDiameterHost(ANY_DIAMETER_HOST);
    expected.setDiameterRealm(ANY_DIAMETER_REALM);
    assertThat(maybeImsiScscf)
            .usingRecursiveComparison()
            .ignoringFieldsOfTypes(Date.class)
            .isEqualTo(Optional.of(expected));
  }

  @Test
  void itUpdatesTheScscf() {
    // GIVEN

    // WHEN
    underTest.setScscf(ANY_IMSI, ANY_SCSCF, ANY_DIAMETER_HOST, ANY_DIAMETER_REALM);
    underTest.setScscf(ANY_IMSI, OTHER_SCSCF, OTHER_DIAMETER_HOST, OTHER_DIAMETER_REALM);
    final var maybeImsiScscf = underTest.getScscf(ANY_IMSI);

    // THEN
    final var expected = new ImsiScscf();
    expected.setImsi(imsi);
    expected.setScscf(OTHER_SCSCF);
    expected.setDiameterHost(OTHER_DIAMETER_HOST);
    expected.setDiameterRealm(OTHER_DIAMETER_REALM);
    assertThat(maybeImsiScscf)
            .usingRecursiveComparison()
            .ignoringFieldsOfTypes(Date.class)
            .isEqualTo(Optional.of(expected));
  }

  @Test
  void itReturnsEmptyScscfWhenCleared() {
    // GIVEN

    // WHEN
    underTest.setScscf(ANY_IMSI, ANY_SCSCF, ANY_DIAMETER_HOST, ANY_DIAMETER_REALM);
    underTest.clearScscf(ANY_IMSI);
    final var maybeImsiScscf = underTest.getScscf(ANY_IMSI);

    // THEN
    assertThat(maybeImsiScscf).isEmpty();
  }
}