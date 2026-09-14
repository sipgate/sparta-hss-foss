package com.sipgate.sparta.hss.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.ImsiProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ImsiProfileDaoTest {

  private static final Date CLASS_LOAD_DATE = new Date();

  private ImsiProfileDao underTest;
  private EntityTransaction transaction;
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    final var factory = Persistence.createEntityManagerFactory("integration-test");
    entityManager = factory.createEntityManager();
    transaction = entityManager.getTransaction();

    transaction.begin();
    underTest = new ImsiProfileDao(entityManager, new ImsiDao(entityManager));
  }

  @AfterEach
  void tearDown() {
    transaction.rollback();
  }

  private Imsi persistNewImsi(final String imsi) {
    final var entity = new Imsi();
    entity.setImsi(imsi);
    entity.setState(Imsi.ImsiState.active);
    entity.setType(Imsi.ImsiType.main);
    entityManager.persist(entity);
    return entity;
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
      final var imsi = persistNewImsi("my-imsi-123e0");
      // -
      final var entry = new ImsiProfile(imsi, "some-profile", new Date());
      entityManager.persist(entry);
      // WHEN
      final var actual = underTest.findAll();
      // THEN
      assertThat(actual)
              .hasSize(1)
              .containsEntry("my-imsi-123e0", "some-profile");
    }


    @Test
    void hasSizeTwoIfTwoPresent() {
      // GIVEN
      final var imsi1 = persistNewImsi("my-imsi-123e0");
      final var imsi2 = persistNewImsi("my-imsi-456e0");
      // -
      entityManager.persist(new ImsiProfile(imsi1, "some-profile", new Date()));
      entityManager.persist(new ImsiProfile(imsi2, "other-profile", new Date()));
      // WHEN
      final var actual = underTest.findAll();
      // THEN
      assertThat(actual)
              .hasSize(2)
              .containsEntry("my-imsi-123e0", "some-profile")
              .containsEntry("my-imsi-456e0", "other-profile");
    }
  }

  @Nested
  class SaveOrUpdate {

    @Test
    void insertsNewImsiProfile() {
      // GIVEN
      final var imsi = persistNewImsi("any-new-imsi");
      final var profileName = "any-profile";

      // WHEN
      underTest.saveOrUpdate(imsi.getImsi(), profileName);

      // THEN
      final var actual = entityManager.createQuery("from ImsiProfile ipf", ImsiProfile.class).getResultList();
      assertThat(actual).hasSize(1);
      assertThat(actual.getFirst().getLastUpdate()).isAfter(CLASS_LOAD_DATE);
    }

    @Test
    void updatesExistingImsiProfile() throws InterruptedException {
      // GIVEN
      final var imsi = persistNewImsi("any-new-imsi");

      // WHEN
      underTest.saveOrUpdate(imsi.getImsi(), "profile-before-update");
      final var firstDate = new Date();

      Thread.sleep(100);
      underTest.saveOrUpdate(imsi.getImsi(), "profile-after-update");

      // THEN
      final var actual = entityManager.createQuery("from ImsiProfile ipf", ImsiProfile.class).getResultList();
      assertThat(actual).hasSize(1);

      final var actualEntity = actual.getFirst();
      assertThat(actualEntity.getName()).isEqualTo("profile-after-update");
      assertThat(actualEntity.getLastUpdate())
              .isAfter(CLASS_LOAD_DATE)
              .isAfter(firstDate);
    }

    @Test
    void saveFailsWhenImsiIsUnknown() {
      // GIVEN
      // imsi is unknown

      // WHEN
      Exception actualException = null;
      try {
        underTest.saveOrUpdate("any-unknown-imsi", "profile-before-update");
      } catch (final Exception e) {
        actualException = e;
      }


      // THEN
      assertThat(actualException).isInstanceOf(IllegalArgumentException.class);

      // imsi was not created
      final var actual = entityManager.createQuery("from ImsiProfile ipf", ImsiProfile.class).getResultList();
      assertThat(actual).isEmpty();
    }

  }

  @Nested
  class Delete {

    @Test
    void deletesExistingImsiProfile() {
      // GIVEN
      final var imsi1 = persistNewImsi("imsi1");
      final var imsi2 = persistNewImsi("imsi2");

      entityManager.persist(new ImsiProfile(imsi1, "profile-before-update", new Date()));
      entityManager.persist(new ImsiProfile(imsi2, "profile-before-update", new Date()));

      assertThat(entityManager.createQuery("from ImsiProfile ipf", ImsiProfile.class).getResultList()).hasSize(2);

      // WHEN
      underTest.delete(imsi1.getImsi());

      // THEN
      final var actual = entityManager.createQuery("from ImsiProfile ipf", ImsiProfile.class).getResultList();
      assertThat(actual).hasSize(1);
      assertThat(actual.getFirst().getImsi().getImsi()).isEqualTo("imsi2");
    }

    @Test
    void deleteIgnoresImsiWithoutProfile() {
      // GIVEN
      final var imsi1 = persistNewImsi("imsi1");
      final var imsi2 = persistNewImsi("imsi2");

      entityManager.persist(new ImsiProfile(imsi2, "profile-before-update", new Date()));

      assertThat(entityManager.createQuery("from ImsiProfile ipf", ImsiProfile.class).getResultList()).hasSize(1);

      // WHEN
      underTest.delete(imsi1.getImsi());

      // THEN
      final var actual = entityManager.createQuery("from ImsiProfile ipf", ImsiProfile.class).getResultList();
      assertThat(actual).hasSize(1);
      assertThat(actual.getFirst().getImsi().getImsi()).isEqualTo("imsi2");
    }

    @Test
    void deleteIgnoresUnknownImsi() {
      // GIVEN
      final var imsi2 = persistNewImsi("imsi2");

      entityManager.persist(new ImsiProfile(imsi2, "profile-before-update", new Date()));

      assertThat(entityManager.createQuery("from ImsiProfile ipf", ImsiProfile.class).getResultList()).hasSize(1);

      // WHEN
      underTest.delete("unknown-imsi");

      // THEN
      final var actual = entityManager.createQuery("from ImsiProfile ipf", ImsiProfile.class).getResultList();
      assertThat(actual).hasSize(1);
      assertThat(actual.getFirst().getImsi().getImsi()).isEqualTo("imsi2");
    }

  }

  @Nested
  class FindByImsi {
    @BeforeEach
    void setUp() {
      // GIVEN
      final var imsi1 = persistNewImsi("imsi1");
      final var imsi2 = persistNewImsi("imsi2");

      entityManager.persist(new ImsiProfile(imsi1, "profile1", new Date()));
      entityManager.persist(new ImsiProfile(imsi2, "profile2", new Date()));
    }

    @Test
    void returnProfileWhenAssigned() {
      // GIVEN

      // WHEN
      final var actual = underTest.findByImsi("imsi1");

      // THEN
      assertThat(actual).isPresent().contains("profile1");
    }

    @Test
    void returnEmptyWhenImsiNotAssigned() {
      // GIVEN

      // WHEN
      final var actual = underTest.findByImsi("unassigned-imsi");

      // THEN
      assertThat(actual).isEmpty();
    }

  }


}