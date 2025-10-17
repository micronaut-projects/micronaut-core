package io.micronaut.http.server.netty.fuzzing;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class FlagAppender extends AppenderBase<ILoggingEvent> {
    private static volatile boolean triggered = false;

    public static void clear() {
        triggered = false;
    }

    public static void checkTriggered() {
        if (triggered) {
            triggered = false;
            throw new RuntimeException("Log message recorded, failing.");
        }
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        triggered = true;
    }
}
