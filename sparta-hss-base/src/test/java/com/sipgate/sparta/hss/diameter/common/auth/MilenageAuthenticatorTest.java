package com.sipgate.sparta.hss.diameter.common.auth;

import static jakarta.xml.bind.DatatypeConverter.parseHexBinary;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sipgate.sparta.hss.persistence.entities.Imsi;
import com.sipgate.sparta.hss.persistence.entities.Sim;
import java.util.Collections;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MilenageAuthenticatorTest {
    @Test
    void itResyncsWithArbitraryAmf() {
        // GIVEN
        final var randString = "23553cbe9637a89d218ae64dae47bf35";
        final var f1StarString = "01cfaf9ec4e871e9";
        final var macsString = f1StarString;
        final var autsString = "BA853F3C123C" + macsString;

        final var k = parseHexBinary("465b5ce8b199b49faa5f0a2ee238a6bc");
        final var op = parseHexBinary("cdc202d5123e20f62b6d676ac72cb318");
        final var sqn = 0xff9bb4d0b607L;

        final var resyncInfo = ResyncInfo.fromConcatenated(parseHexBinary(randString + autsString));

        final var sim = new Sim();
        sim.setSecretKey(k);
        sim.setOp(op);
        sim.setSqn(1L);
        sim.setImsis(Collections.singleton(new Imsi()));

        final var amf = new byte[]{(byte) 0xb9, (byte) 0xb9};

        final var underTest = new MilenageAuthenticator(
                null,
                new SimpleMeterRegistry(),
                StoredKeyFormat.OP
        );

        final var resyncLogger = mock(MilenageLogger.class);
        final var input = new MilenageInput(amf, resyncInfo, sim);

        // WHEN
        final var success = underTest.resyncSQN(resyncLogger, input);

        // THEN
        assertThat(success).isTrue();
        assertThat(sim.getSqn()).isEqualTo(sqn + 10);
    }

    @Test
    void itUsesTheStoredValueDirectlyAsOpcInOpcMode() throws Exception {
        // GIVEN — 3GPP TS 35.207/35.208 Test Set 1 key material. One SIM stores OP, the other
        // the pre-computed OPc; with the same RAND both modes must produce the same vector.
        final var k = parseHexBinary("465b5ce8b199b49faa5f0a2ee238a6bc");
        final var op = parseHexBinary("cdc202d5123e20f62b6d676ac72cb318");
        final var opc = Milenage.computeOpC(k, op);
        final var amf = new byte[]{(byte) 0x80, 0x00};

        final var fixedRandGen = mock(RandomGenerator.class);
        when(fixedRandGen.nextRand(16)).thenReturn(parseHexBinary("23553cbe9637a89d218ae64dae47bf35"));

        // WHEN
        final var fromOp = new MilenageAuthenticator(fixedRandGen, new SimpleMeterRegistry(), StoredKeyFormat.OP)
                .generate3GAuthenticationVector(mock(MilenageLogger.class), new MilenageInput(amf, null, simWith(k, op)));
        final var fromOpc = new MilenageAuthenticator(fixedRandGen, new SimpleMeterRegistry(), StoredKeyFormat.OPC)
                .generate3GAuthenticationVector(mock(MilenageLogger.class), new MilenageInput(amf, null, simWith(k, opc)));

        // THEN
        assertThat(fromOpc.rand()).isEqualTo(fromOp.rand());
        assertThat(fromOpc.autn()).isEqualTo(fromOp.autn());
        assertThat(fromOpc.xres()).isEqualTo(fromOp.xres());
        assertThat(fromOpc.confidentialityKey()).isEqualTo(fromOp.confidentialityKey());
        assertThat(fromOpc.integrityKey()).isEqualTo(fromOp.integrityKey());
    }

    @Test
    void itResyncsWithTheStoredOpcInOpcMode() {
        // GIVEN — the resync scenario from above, but the SIM stores the pre-computed OPc
        final var k = parseHexBinary("465b5ce8b199b49faa5f0a2ee238a6bc");
        final var opc = Milenage.computeOpC(k, parseHexBinary("cdc202d5123e20f62b6d676ac72cb318"));
        final var sqn = 0xff9bb4d0b607L;

        final var resyncInfo = ResyncInfo.fromConcatenated(
                parseHexBinary("23553cbe9637a89d218ae64dae47bf35" + "BA853F3C123C" + "01cfaf9ec4e871e9"));
        final var sim = simWith(k, opc);
        final var amf = new byte[]{(byte) 0xb9, (byte) 0xb9};

        final var underTest = new MilenageAuthenticator(null, new SimpleMeterRegistry(), StoredKeyFormat.OPC);

        // WHEN
        final var success = underTest.resyncSQN(mock(MilenageLogger.class), new MilenageInput(amf, resyncInfo, sim));

        // THEN
        assertThat(success).isTrue();
        assertThat(sim.getSqn()).isEqualTo(sqn + 10);
    }

    private static Sim simWith(final byte[] secretKey, final byte[] operatorKey) {
        final var sim = new Sim();
        sim.setSecretKey(secretKey);
        sim.setOp(operatorKey);
        sim.setSqn(1L);
        sim.setImsis(Collections.singleton(new Imsi()));
        return sim;
    }
}