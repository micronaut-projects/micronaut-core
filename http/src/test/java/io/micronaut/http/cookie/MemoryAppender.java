package io.micronaut.http.cookie;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.ArrayList;
import java.util.List;

class MemoryAppender extends AppenderBase<ILoggingEvent> {
    List<String> events = new ArrayList<>();

    @Override
    protected void append(ILoggingEvent e) {
        events.add(e.getFormattedMessage());
    }

    public List<String> getEvents() {
        return this.events;
    }
}
