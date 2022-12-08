package io.micronaut.http.client.netty

import ch.qos.logback.classic.Level
import org.slf4j.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.http.util.HttpHeadersUtil
import io.netty.handler.codec.http.DefaultHttpHeaders
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class DefaultClientHeaderMaskTest extends Specification {

    def "check mask detects common security headers"() {
        given:
        MemoryAppender appender = new MemoryAppender()
        Logger log = LoggerFactory.getLogger(DefaultHttpClient.class)

        expect:
        log instanceof ch.qos.logback.classic.Logger

        when:
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) log
        logger.addAppender(appender)
        logger.setLevel(Level.TRACE)
        appender.start()

        DefaultHttpHeaders headers = new DefaultHttpHeaders()
        headers.add("Authorization", "Bearer foo")
        headers.add("Proxy-Authorization", "AWS4-HMAC-SHA256 bar")
        headers.add("Cookie", "baz")
        headers.add("Set-Cookie", "qux")
        headers.add("X-Forwarded-For", "quux")
        headers.add("X-Forwarded-Host", "quuz")
        headers.add("X-Real-IP", "waldo")
        headers.add("X-Forwarded-For", "fred")
        headers.add("Credential", "foo")
        headers.add("Signature", "bar probably secret")

        HttpHeadersUtil.trace(log, headers.names(), headers::getAll)

        then:
        appender.events.size() == headers.size()
        appender.events.join("\n") == """Authorization: *MASKED*
            |Proxy-Authorization: *MASKED*
            |Cookie: baz
            |Set-Cookie: qux
            |X-Forwarded-For: quux
            |X-Forwarded-For: fred
            |X-Forwarded-Host: quuz
            |X-Real-IP: waldo
            |Credential: *MASKED*
            |Signature: *MASKED*""".stripMargin()

        cleanup:
        appender.stop()
    }

    static class MemoryAppender extends AppenderBase<ILoggingEvent> {
        final BlockingQueue<String> events = new LinkedBlockingQueue<>()

        @Override
        protected void append(ILoggingEvent e) {
            events.add(e.formattedMessage)
        }
    }
}
