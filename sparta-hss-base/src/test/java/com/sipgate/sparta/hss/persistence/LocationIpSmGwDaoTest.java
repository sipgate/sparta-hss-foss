package com.sipgate.sparta.hss.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.Imsi.ImsiState;
import com.sipgate.sparta.hss.persistence.entities.Imsi.ImsiType;
import com.sipgate.sparta.hss.persistence.entities.LocationIpSmGw;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Persistence;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocationIpSmGwDaoTest {

  private static final String ANY_IMSI = "any-imsi";
  private static final String ANY_GW_NAME = "ucn01.dev.sgwl.epc.mnc003.mcc262.3gppnetwork.org";
  private static final String ANY_GW_REALM = "epc.mnc003.mcc262.3gppnetwork.org";

  private static final String OTHER_GW_NAME = "ucn02.dev.sgwl.epc.mnc003.mcc262.3gppnetwork.org";
  private static final String OTHER_GW_REALM = "other-realm.example.org";

  private LocationIpSmGwDao underTest;
  private EntityTransaction transaction;
  private EntityManager entityManager;
  private Imsi imsi;

  @BeforeEach
  void setUp() {
    final var factory = Persistence.createEntityManagerFactory("integration-test");
    entityManager = factory.createEntityManager();
    transaction = entityManager.getTransaction();

    transaction.begin();
    imsi = new Imsi();
    imsi.setImsi(ANY_IMSI);
    imsi.setState(ImsiState.active);
    imsi.setType(ImsiType.main);
    imsi.setAssignedAt(new Date());
    entityManager.persist(imsi);
    underTest = new LocationIpSmGwDao(entityManager);
  }

  @AfterEach
  void tearDown() {
    transaction.rollback();
  }

  private long countRows() {
    return entityManager
        .createQuery("select count(s) from LocationIpSmGw s where s.imsi.imsi = :imsi", Long.class)
        .setParameter("imsi", ANY_IMSI)
        .getSingleResult();
  }

  @Test
  void itReturnsEmptyIpSmGwInitially() {
    // GIVEN

    // WHEN
    final var maybeIpSmGw = underTest.getIpSmGw(ANY_IMSI);

    // THEN
    assertThat(maybeIpSmGw).isEmpty();
  }

  @Test
  void itReturnsIpSmGwWhenStored() {
    // GIVEN

    // WHEN
    underTest.store(ANY_IMSI, ANY_GW_NAME, ANY_GW_REALM);
    final var maybeIpSmGw = underTest.getIpSmGw(ANY_IMSI);

    // THEN
    final var expected = new LocationIpSmGw();
    expected.setImsi(imsi);
    expected.setIpSmGwName(ANY_GW_NAME);
    expected.setIpSmGwRealm(ANY_GW_REALM);
    assertThat(maybeIpSmGw)
            .usingRecursiveComparison()
            .ignoringFieldsOfTypes(Date.class)
            .isEqualTo(Optional.of(expected));
  }

  @Test
  void itUpdatesTheIpSmGw() {
    // GIVEN

    // WHEN
    underTest.store(ANY_IMSI, ANY_GW_NAME, ANY_GW_REALM);
    underTest.store(ANY_IMSI, OTHER_GW_NAME, OTHER_GW_REALM);
    final var maybeIpSmGw = underTest.getIpSmGw(ANY_IMSI);

    // THEN
    final var expected = new LocationIpSmGw();
    expected.setImsi(imsi);
    expected.setIpSmGwName(OTHER_GW_NAME);
    expected.setIpSmGwRealm(OTHER_GW_REALM);
    assertThat(maybeIpSmGw)
            .usingRecursiveComparison()
            .ignoringFieldsOfTypes(Date.class)
            .isEqualTo(Optional.of(expected));

    // THEN the repeated store updated the existing row instead of creating a duplicate
    assertThat(countRows()).isEqualTo(1L);
  }

  @Test
  void itRejectsStoringForUnknownImsi() {
    // GIVEN no Imsi entity exists for the requested IMSI

    // WHEN / THEN storing IP-SM-GW information for it fails loudly instead of being ignored
    assertThatThrownBy(() -> underTest.store("unknown-imsi", ANY_GW_NAME, ANY_GW_REALM))
        .isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(NoResultException.class);
    assertThat(countRows()).isEqualTo(0L);
  }

  @Test
  void itReturnsEmptyIpSmGwWhenCleared() {
    // GIVEN

    // WHEN
    underTest.store(ANY_IMSI, ANY_GW_NAME, ANY_GW_REALM);
    underTest.clearIpSmGw(ANY_IMSI);
    final var maybeIpSmGw = underTest.getIpSmGw(ANY_IMSI);

    // THEN
    assertThat(maybeIpSmGw).isEmpty();
  }

  @Test
  void itIgnoresClearWhenNothingStored() {
    // GIVEN

    // WHEN / THEN
    underTest.clearIpSmGw(ANY_IMSI);
    assertThat(underTest.getIpSmGw(ANY_IMSI)).isEmpty();
  }

  @Test
  void itPropagatesQueryErrorsInsteadOfTreatingThemAsNotFound() {
    // GIVEN
    entityManager.close();

    // WHEN / THEN
    assertThatThrownBy(() -> underTest.getIpSmGw(ANY_IMSI))
        .isInstanceOf(IllegalStateException.class);
  }
}
