package com.sipgate.sparta.hss.configuration;

import com.sipgate.sparta.hss.persistence.BlockRoamingDao;
import com.sipgate.sparta.hss.persistence.EffectiveProfileDao;
import com.sipgate.sparta.hss.persistence.ImsiDao;
import com.sipgate.sparta.hss.persistence.ImsiProfileDao;
import com.sipgate.sparta.hss.persistence.ImsiScscfDao;
import com.sipgate.sparta.hss.persistence.LocationLteDao;
import com.sipgate.sparta.hss.persistence.LocationIpSmGwDao;
import com.sipgate.sparta.hss.persistence.LocationVowifiDao;
import com.sipgate.sparta.hss.persistence.MsisdnDao;
import com.sipgate.sparta.hss.persistence.MsisdnScscfDao;
import com.sipgate.sparta.hss.persistence.SimDao;
import com.sipgate.sparta.hss.persistence.TacProfileDao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/// Provides the base module's DAOs as beans. The DAOs receive a shared [EntityManager] proxy that
/// delegates to the transaction-bound EntityManager of the current thread, so the singleton DAOs
/// are safe for concurrent use.
@AutoConfiguration
public class HssPersistenceConfiguration {

    @Bean
    public EntityManager sharedEntityManager(final EntityManagerFactory entityManagerFactory) {
        return SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    }

    @Bean
    public SimDao simDao(final EntityManager entityManager) {
        return new SimDao(entityManager);
    }

    @Bean
    public ImsiDao imsiDao(final EntityManager entityManager) {
        return new ImsiDao(entityManager);
    }

    @Bean
    public ImsiProfileDao imsiProfileDao(final EntityManager entityManager, final ImsiDao imsiDao) {
        return new ImsiProfileDao(entityManager, imsiDao);
    }

    @Bean
    public ImsiScscfDao imsiScscfDao(final EntityManager entityManager) {
        return new ImsiScscfDao(entityManager);
    }

    @Bean
    public MsisdnDao msisdnDao(final EntityManager entityManager) {
        return new MsisdnDao(entityManager);
    }

    @Bean
    public MsisdnScscfDao msisdnScscfDao(final EntityManager entityManager) {
        return new MsisdnScscfDao(entityManager);
    }

    @Bean
    public LocationLteDao locationLteDao(final EntityManager entityManager, final ImsiDao imsiDao) {
        return new LocationLteDao(entityManager, imsiDao);
    }

    @Bean
    public LocationVowifiDao locationVowifiDao(final EntityManager entityManager) {
        return new LocationVowifiDao(entityManager);
    }

    @Bean
    public LocationIpSmGwDao locationIpSmGwDao(final EntityManager entityManager) {
        return new LocationIpSmGwDao(entityManager);
    }

    @Bean
    public TacProfileDao tacProfileDao(final EntityManager entityManager) {
        return new TacProfileDao(entityManager);
    }

    @Bean
    public EffectiveProfileDao effectiveProfileDao(final EntityManager entityManager) {
        return new EffectiveProfileDao(entityManager);
    }

    @Bean
    public BlockRoamingDao blockRoamingDao(final EntityManager entityManager) {
        return new BlockRoamingDao(entityManager);
    }
}
