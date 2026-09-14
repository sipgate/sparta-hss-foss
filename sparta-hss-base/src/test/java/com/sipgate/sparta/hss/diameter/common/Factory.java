package com.sipgate.sparta.hss.diameter.common;

import com.sipgate.sparta.diameter.base.session.DiameterNodeConfig;
import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.Imsi.ImsiState;
import com.sipgate.sparta.hss.persistence.entities.Imsi.ImsiType;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import java.net.InetAddress;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.xml.bind.DatatypeConverter;

public class Factory {

    /// A minimal node config for tests that only need origin host/realm (e.g. Session-Id
    /// generation); no applications are advertised.
    public static DiameterNodeConfig nodeConfig(final String originHost, final String originRealm) {
        return new DiameterNodeConfig(
                originHost,
                originRealm,
                List.of(InetAddress.getLoopbackAddress()),
                0L,
                "sparta-hss-test",
                new DiameterNodeConfig.Capabilities(List.of(), List.of(), List.of(), List.of()),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30));
    }

    public static Set<Imsi> buildImsiSet(final Imsi imsi) {
        final Set<Imsi> imsiSet = new HashSet<>();
        imsiSet.add(imsi);
        return imsiSet;
    }

    public static Imsi buildImsi() {
        final var imsi = new Imsi();
        imsi.setImsi("999990000104857");
        imsi.setType(ImsiType.main);
        imsi.setState(ImsiState.active);
        return imsi;
    }

    public static Sim buildSim(final Set<Imsi> imsiSet) {
        final var sim = new Sim();
        sim.setImsis(imsiSet);
        sim.setSqn(308L);
        sim.setSecretKey(
                DatatypeConverter.parseHexBinary("E49DA901CDE1F94D708FE61AF8D7501D"));
        sim.setOp(
                DatatypeConverter.parseHexBinary("02F1869C0962A4FDFAD28DA50065BA66"));
        sim.setIccid("89493100000001048578");
        return sim;
    }
}
