package io.micronaut.logback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import groovy.transform.CompileStatic
import groovy.transform.PackageScope

@PackageScope
@CompileStatic
class MemoryAppender extends AppenderBase<ILoggingEvent> {
    List<String> events = []

    @Override
    protected void append(ILoggingEvent e) {
        events << e.formattedMessage
    }
}
