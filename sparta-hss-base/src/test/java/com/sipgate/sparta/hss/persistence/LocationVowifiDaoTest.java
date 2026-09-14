package com.sipgate.sparta.hss.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.Imsi.ImsiState;
import com.sipgate.sparta.hss.persistence.entities.Imsi.ImsiType;
import com.sipgate.sparta.hss.persistence.entities.LocationVowifi;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocationVowifiDaoTest {

  private static final String ANY_IMSI = "any-imsi";
  private static final String ANY_AAA_SERVER = "any-aaa-server.example.org";
  private static final String ANY_DIAMETER_HOST = "any-diameter-host.example.org";
  private static final String ANY_DIAMETER_REALM = "any-diameter-realm.example.org";

  private static final String OTHER_AAA_SERVER = "other-aaa-server";
  private static final String OTHER_DIAMETER_HOST = "other-diameter-host.example.org";
  private static final String OTHER_DIAMETER_REALM = "other-diameter-realm.example.org";

  private LocationVowifiDao underTest;
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
    underTest = new LocationVowifiDao(entityManager);
  }

  @AfterEach
  void tearDown() {
    transaction.rollback();
  }

  @Test
  void itReturnsEmptyAaaServerInitially() {
    // GIVEN

    // WHEN
    final var maybeLocationVowifi = underTest.getAaaServer(ANY_IMSI);

    // THEN
    assertThat(maybeLocationVowifi).isEmpty();
  }

  @Test
  void itReturnsAaaServerWhenStored() {
    // GIVEN

    // WHEN
    underTest.store(ANY_IMSI, ANY_AAA_SERVER, ANY_DIAMETER_HOST, ANY_DIAMETER_REALM);
    final var maybeLocationVowifi = underTest.getAaaServer(ANY_IMSI);

    // THEN
    final var expected = new LocationVowifi();
    expected.setImsi(imsi);
    expected.setAaaServerName(ANY_AAA_SERVER);
    expected.setDiameterHost(ANY_DIAMETER_HOST);
    expected.setDiameterRealm(ANY_DIAMETER_REALM);
    assertThat(maybeLocationVowifi)
            .usingRecursiveComparison()
            .ignoringFieldsOfTypes(Date.class)
            .isEqualTo(Optional.of(expected));
  }

  @Test
  void itUpdatesTheAaaServer() {
    // GIVEN

    // WHEN
    underTest.store(ANY_IMSI, ANY_AAA_SERVER, ANY_DIAMETER_HOST, ANY_DIAMETER_REALM);
    underTest.store(ANY_IMSI, OTHER_AAA_SERVER, OTHER_DIAMETER_HOST, OTHER_DIAMETER_REALM);
    final var maybeLocationVowifi = underTest.getAaaServer(ANY_IMSI);

    // THEN
    final var expected = new LocationVowifi();
    expected.setImsi(imsi);
    expected.setAaaServerName(OTHER_AAA_SERVER);
    expected.setDiameterHost(OTHER_DIAMETER_HOST);
    expected.setDiameterRealm(OTHER_DIAMETER_REALM);
    assertThat(maybeLocationVowifi)
            .usingRecursiveComparison()
            .ignoringFieldsOfTypes(Date.class)
            .isEqualTo(Optional.of(expected));
  }

  @Test
  void itReturnsEmptyAaaServerWhenCleared() {
    // GIVEN

    // WHEN
    underTest.store(ANY_IMSI, ANY_AAA_SERVER, ANY_DIAMETER_HOST, ANY_DIAMETER_REALM);
    underTest.clearAaaServer(ANY_IMSI);
    final var maybeLocationVowifi = underTest.getAaaServer(ANY_IMSI);

    // THEN
    assertThat(maybeLocationVowifi).isEmpty();
  }

  @Test
  void itPropagatesQueryErrorsInsteadOfTreatingThemAsNotFound() {
    // GIVEN
    entityManager.close();

    // WHEN / THEN
    assertThatThrownBy(() -> underTest.getAaaServer(ANY_IMSI))
        .isInstanceOf(IllegalStateException.class);
  }
}
