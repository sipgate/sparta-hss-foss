package com.sipgate.sparta.hss.diameter.common.auth;

import com.sipgate.sparta.hss.persistence.entities.Sim;
import java.util.Objects;

public record MilenageInput(
        byte[] amf,
        ResyncInfo resyncInfo,
        Sim sim
) {
    public long updateSimSqn(final MilenageLogger milenageLogger, final UpdateSimSqnFunction updateFunction) throws Exception {
        final var oldSqn = sim.getSqn();
        final var newSqn = updateFunction.apply(oldSqn);
        if (Objects.equals(oldSqn, newSqn)) {
            milenageLogger.log("[MI16] leave SIM SQN as is because updater returned the old value: {}", oldSqn);
            return oldSqn;
        }

        sim.setSqn(newSqn);

        // use getter to make sure we get the value that is managed by the entity manager
        final var effectiveSqn = sim.getSqn();
        milenageLogger.log("[MI18] updating SIM SQN: {} -> {}", oldSqn, effectiveSqn);
        return effectiveSqn;
    }

    @FunctionalInterface
    public interface UpdateSimSqnFunction {
        long apply(final Long t) throws Exception;
    }
}
