package com.sipgate.sparta.hss.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.persistence.entities.TacProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TacProfileDaoTest {

  private static final String ANY_PROFILE = "volte";
  private static final String OTHER_PROFILE = "no-volte";

  private static final String ANY_TAC = "12345678";
  private static final String OTHER_TAC = "87654321";

  private EntityTransaction transaction;
  private EntityManager entityManager;
  private TacProfileDao underTest;

  @BeforeEach
  void setUp() {
    final var factory = Persistence.createEntityManagerFactory("integration-test");
    entityManager = factory.createEntityManager();
    transaction = entityManager.getTransaction();

    transaction.begin();
    underTest = new TacProfileDao(entityManager);
  }

  @AfterEach
  void tearDown() {
    transaction.rollback();
  }

  @Nested
  class FindAll {
    @Test
    void isEmptyIfNoneFound() {
      // GIVEN
      // - nothing -
      // WHEN
      final var actual = underTest.findAll();
      // THEN
      assertThat(actual).isEmpty();
    }


    @Test
    void isNotEmptyIfAnyFound() {
      // GIVEN
      final var entry = new TacProfile(ANY_TAC, ANY_PROFILE, new Date());
      entityManager.persist(entry);
      // WHEN
      final var actual = underTest.findAll();
      // THEN
      assertThat(actual)
              .hasSize(1)
              .containsEntry(ANY_TAC, ANY_PROFILE);
    }


    @Test
    void hasSizeTwoIfTwoPresent() {
      // GIVEN
      entityManager.persist(new TacProfile(ANY_TAC, ANY_PROFILE, new Date()));
      entityManager.persist(new TacProfile(OTHER_TAC, OTHER_PROFILE, new Date()));
      // WHEN
      final var actual = underTest.findAll();
      // THEN
      assertThat(actual)
              .hasSize(2)
              .containsEntry(ANY_TAC, ANY_PROFILE)
              .containsEntry(OTHER_TAC, OTHER_PROFILE);
    }
  }

  @Nested
  class SaveOrUpdate {

    @Test
    void insertsNewTacProfile() throws InterruptedException {
      // GIVEN
      final var before = new Date();
      Thread.sleep(100);

      // WHEN
      underTest.saveOrUpdate(ANY_TAC, ANY_PROFILE);

      // THEN
      final var actual = entityManager.createQuery("from TacProfile ipf", TacProfile.class).getResultList();
      assertThat(actual).hasSize(1);
      assertThat(actual.getFirst().getLastUpdate()).isAfter(before);
    }

    @Test
    void updatesExistingTacProfile() throws InterruptedException {
      // GIVEN
      final var before = new Date();

      // WHEN
      underTest.saveOrUpdate(ANY_TAC, ANY_PROFILE);
      final var firstDate = new Date();

      Thread.sleep(100);
      underTest.saveOrUpdate(ANY_TAC, OTHER_PROFILE);

      // THEN
      final var actual = entityManager.createQuery("from TacProfile ipf", TacProfile.class).getResultList();
      assertThat(actual).hasSize(1);

      final var actualEntity = actual.getFirst();
      assertThat(actualEntity.getName()).isEqualTo(OTHER_PROFILE);
      assertThat(actualEntity.getLastUpdate())
              .isAfter(before)
              .isAfter(firstDate);
    }

  }

  @Nested
  class Delete {

    @Test
    void deletesExistingTacProfile() {
      // GIVEN
      entityManager.persist(new TacProfile(ANY_TAC, ANY_PROFILE, new Date()));
      entityManager.persist(new TacProfile(OTHER_TAC, ANY_PROFILE, new Date()));

      assertThat(entityManager.createQuery("from TacProfile ipf", TacProfile.class).getResultList()).hasSize(2);

      // WHEN
      underTest.delete(ANY_TAC);

      // THEN
      final var actual = entityManager.createQuery("from TacProfile ipf", TacProfile.class).getResultList();
      assertThat(actual).hasSize(1);
      assertThat(actual.getFirst().getTac()).isEqualTo(OTHER_TAC);
    }

    @Test
    void doesNotDeleteWrongTac() {
      // GIVEN
      entityManager.persist(new TacProfile(OTHER_TAC, ANY_PROFILE, new Date()));

      assertThat(entityManager.createQuery("from TacProfile ipf", TacProfile.class).getResultList()).hasSize(1);

      // WHEN
      underTest.delete(ANY_TAC);

      // THEN
      final var actual = entityManager.createQuery("from TacProfile ipf", TacProfile.class).getResultList();
      assertThat(actual).hasSize(1);
      assertThat(actual.getFirst().getTac()).isEqualTo(OTHER_TAC);
    }

  }

  @Nested
  class FindByTac {
    @Test
    void returnProfileWhenAssigned() {
      // GIVEN
      entityManager.persist(new TacProfile(ANY_TAC, ANY_PROFILE, new Date()));

      // WHEN
      final var actual = underTest.findByTac(ANY_TAC);

      // THEN
      assertThat(actual).contains(ANY_PROFILE);
    }

    @Test
    void returnEmptyWhenImsiNotAssigned() {
      // GIVEN

      // WHEN
      final var actual = underTest.findByTac("12345678");

      // THEN
      assertThat(actual).isEmpty();
    }

    @Test
    void itHandlesNullQuery() {
      // GIVEN

      // WHEN
      final var actual = underTest.findByTac(null);

      // THEN
      assertThat(actual).isEmpty();
    }
  }
}