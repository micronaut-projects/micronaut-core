package io.micronaut.http

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MemoryAppender extends AppenderBase<ILoggingEvent> {
    private final BlockingQueue<String> events = new LinkedBlockingQueue<>()

    @Override
    protected void append(ILoggingEvent e) {
        events.add(e.formattedMessage)
    }

    Queue<String> getEvents() {
        return events
    }

    String headLog(long timeout) {
        return events.poll(timeout, TimeUnit.SECONDS)
    }
}
