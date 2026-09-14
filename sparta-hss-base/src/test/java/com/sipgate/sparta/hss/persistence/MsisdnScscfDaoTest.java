package com.sipgate.sparta.hss.persistence;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.ImsiScscf;
import com.sipgate.sparta.hss.persistence.entities.Msisdn;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class MsisdnScscfDaoTest {

    private static final String ANY_MSISDN = "any-msisdn";
    private static final String ANY_SCSCF = "any-scscf.example.org";
    private static final String ANY_IMSI = "any-imsi";
    private static final String ANY_ICCID = "any-iccid";

    private MsisdnScscfDao underTest;
    private EntityTransaction transaction;
    private EntityManager entityManager;
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
        sim.setMsisdn(msisdn);
        sim.setIccid(ANY_ICCID);
        sim.setOp(new byte[]{});
        sim.setSecretKey(new byte[]{});
        entityManager.persist(sim);

        imsi = new Imsi();
        imsi.setImsi(ANY_IMSI);
        imsi.setState(Imsi.ImsiState.active);
        imsi.setType(Imsi.ImsiType.main);
        imsi.setAssignedAt(new Date());
        imsi.setSim(sim);
        entityManager.persist(imsi);

        underTest = new MsisdnScscfDao(entityManager);
    }

    @AfterEach
    void tearDown() {
        transaction.rollback();
    }

    @Nested
    public class GetScscf {
        @Test
        void itReturnsEmptyScscfInitially() {
            // GIVEN

            // WHEN
            final var maybeScscf = underTest.getScscf(ANY_MSISDN);

            // THEN
            assertThat(maybeScscf).isEmpty();
        }

        @Test
        void itReturnsScscfWhenStored() {
            // GIVEN
            final var imsiScscf = new ImsiScscf();
            imsiScscf.setImsi(imsi);
            imsiScscf.setScscf(ANY_SCSCF);
            imsiScscf.setDiameterHost("any-diameter-host");
            imsiScscf.setDiameterRealm("any-diameter-realm");
            entityManager.persist(imsiScscf);

            // WHEN
            final var maybeScscf = underTest.getScscf(ANY_MSISDN);

            // THEN
            assertThat(maybeScscf).contains(ANY_SCSCF);
        }

    }

    @Nested
    class GetScscfRecent {

        @Test
        void itOmitsOutdatedEntriesWhenQueriedForRecency() {
            // GIVEN
            final var imsiScscf = new ImsiScscf();
            imsiScscf.setImsi(imsi);
            imsiScscf.setScscf(ANY_SCSCF);
            imsiScscf.setDiameterHost("any-diameter-host");
            imsiScscf.setDiameterRealm("any-diameter-realm");
            imsiScscf.setLastUpdate(Date.from(Instant.now().minus(2, ChronoUnit.DAYS)));
            entityManager.persist(imsiScscf);

            // WHEN
            final var maybeScscf = underTest.getScscfRecent(ANY_MSISDN);

            // THEN
            assertThat(maybeScscf).isEmpty();
        }

        @Test
        void itReturnsEntryWhenQueriedForRecency() {
            // GIVEN
            final var imsiScscf = new ImsiScscf();
            imsiScscf.setImsi(imsi);
            imsiScscf.setScscf(ANY_SCSCF);
            imsiScscf.setDiameterHost("any-diameter-host");
            imsiScscf.setDiameterRealm("any-diameter-realm");
            imsiScscf.setLastUpdate(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)));
            entityManager.persist(imsiScscf);

            // WHEN
            final var maybeScscf = underTest.getScscfRecent(ANY_MSISDN);

            // THEN
            assertThat(maybeScscf).contains(ANY_SCSCF);
        }
    }
}