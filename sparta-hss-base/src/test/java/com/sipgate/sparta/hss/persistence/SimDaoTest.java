package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class SimDaoTest {
    private static final String KNOWN_IMSI = "known-imsi";

    private SimDao underTest;
    private EntityTransaction transaction;
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        final var factory = Persistence.createEntityManagerFactory("integration-test");
        entityManager = factory.createEntityManager();
        transaction = entityManager.getTransaction();

        transaction.begin();
        underTest = new SimDao(entityManager);
    }

    @AfterEach
    void tearDown() {
        transaction.rollback();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void itSelectsSim(final boolean isActive) {
        // GIVEN
        final var sim = new Sim();
        sim.setSecretKey(new byte[]{});
        sim.setOp(new byte[]{});
        sim.setIccid("iccid");
        sim.setActive(isActive);
        entityManager.persist(sim);

        final var imsi = new Imsi();
        imsi.setSim(sim);

        imsi.setImsi(KNOWN_IMSI);
        imsi.setType(Imsi.ImsiType.main);
        imsi.setAssignedAt(new Date());
        entityManager.persist(imsi);

        // WHEN
        final var actual = underTest.getSimByImsiString(KNOWN_IMSI);

        // THEN
        assertThat(actual).contains(sim);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void itSelectsSimByIccid(final boolean isActive) {
        // GIVEN
        final var sim = new Sim();
        sim.setSecretKey(new byte[]{});
        sim.setOp(new byte[]{});
        sim.setIccid("8988173000000123456");
        sim.setActive(isActive);
        entityManager.persist(sim);

        // WHEN
        final var actual = underTest.getSimByIccid("8988173000000123456");

        // THEN
        assertThat(actual).contains(sim);
        assertThat(underTest.getSimByIccid("unknown-iccid")).isEmpty();
    }
}
