package io.micronaut.http.server.netty.ssl

import ch.qos.logback.classic.Logger
import io.micronaut.http.server.netty.configuration.MemoryAppender
import org.slf4j.LoggerFactory

class InMemoryAppender implements AutoCloseable {

    private MemoryAppender appender;
    private final Class<?> loggerClass;

    InMemoryAppender(Class<?> loggerClass) {
        this.loggerClass = loggerClass
        this.appender = new MemoryAppender()
        Logger l = (Logger) LoggerFactory.getLogger(loggerClass)
        l.addAppender(appender)
        appender.start()
    }

    List<String> getEvents() {
        appender.events
    }

    void clear() {
        appender.events.clear()
    }

    @Override
    void close() throws Exception {
        Logger l = (Logger) LoggerFactory.getLogger(loggerClass)
        l.detachAppender(appender)
        appender.stop()
    }
}
