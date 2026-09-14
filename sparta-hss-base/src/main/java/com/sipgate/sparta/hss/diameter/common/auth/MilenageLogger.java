package com.sipgate.sparta.hss.diameter.common.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MilenageLogger implements AutoCloseable {
    private static final String PREFIX = "MILENAGE_LOGGER.";

    private final Logger logger;
    private final String imsi;

    public MilenageLogger(final String imsi) {
        this(imsi, Thread.currentThread().getStackTrace()[2].getClassName());
    }

    private MilenageLogger(final String imsi, final String name) {
        this.imsi = imsi;
        this.logger = LoggerFactory.getLogger(PREFIX + name);
        log("[RL15] entering >>>");
    }

    public void log(final String message) {
        logInternal("{}", message);
    }

    public void log(final String message, final Object arg) {
        logInternal(message, arg);
    }

    public void log(final String message, final Object... messageArgs) {
        logInternal(message, messageArgs);
    }

    private void logInternal(final String message, final Object... messageArgs) {
        if (logger.isDebugEnabled()) {
            final var addLength = logger.isTraceEnabled() ? 5 : 3;
            final var args = new Object[messageArgs.length + addLength];
            System.arraycopy(messageArgs, 0, args, addLength, messageArgs.length);
            args[0] = "MILENAGE_LOGGER";
            args[1] = imsi;

            if (logger.isTraceEnabled()) {
                final var stackElement = Thread.currentThread().getStackTrace()[3];
                args[2] = stackElement.getFileName();
                args[3] = stackElement.getLineNumber();
                args[4] = message;
                logger.trace("{} {} in ({}:{}) - {}", args);
            } else {
                args[2] = message;
                logger.debug("{} {} - {}", args);
            }
        }
    }

    @Override
    public void close() {
        log("[RL40] <<< leaving");
    }
}
