package com.sipgate.sparta.hss;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.Msisdn;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/// The minimal zero-setup database: the application boots on a plain SQLite file and the DAO
/// layer works against it, including the `SELECT ... FOR UPDATE` path that SQLite has no
/// syntax for (the dialect degrades it to a plain SELECT; SQLite locks the whole file on
/// write anyway).
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/sqlite-smoke-test.db",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "spring.jpa.hibernate.ddl-auto=update",
})
class SqliteDatabaseTest {

    @BeforeAll
    static void removeStaleDatabaseFile() throws Exception {
        Files.deleteIfExists(Path.of("target/sqlite-smoke-test.db"));
    }

    @Autowired
    private SimDao simDao;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void itRoundTripsASimThroughTheDaoLayer() {
        final var msisdn = new Msisdn();
        msisdn.setMsisdn("999999000000001");
        entityManager.persist(msisdn);

        final var sim = new Sim();
        sim.setIccid("any-iccid");
        sim.setOp(new byte[16]);
        sim.setSecretKey(new byte[16]);
        sim.setSqn(32L);
        sim.setMsisdn(msisdn);
        entityManager.persist(sim);

        final var imsi = new Imsi();
        imsi.setImsi("999990000000001");
        imsi.setState(Imsi.ImsiState.active);
        imsi.setType(Imsi.ImsiType.main);
        imsi.setAssignedAt(new Date());
        imsi.setSim(sim);
        entityManager.persist(imsi);
        entityManager.flush();
        entityManager.clear();

        final var found = simDao.getSimByImsiStringForUpdate("999990000000001");

        assertThat(found).isPresent();
        assertThat(found.get().getIccid()).isEqualTo("any-iccid");
    }
}
