package io.micronaut.http.client.netty

import ch.qos.logback.classic.Level
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import org.slf4j.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.netty.handler.codec.http.DefaultHttpHeaders
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class DefaultClientHeaderMaskTest extends Specification {

    def "check mask detects common security headers"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        HttpClient client = ctx.createBean(HttpClient, "http://localhost:8080")

        expect:
        client instanceof DefaultHttpClient

        when:
        MemoryAppender appender = new MemoryAppender()
        Logger log = LoggerFactory.getLogger(DefaultHttpClient.class)

        then:
        log instanceof ch.qos.logback.classic.Logger

        when:
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) log
        logger.addAppender(appender)
        logger.setLevel(Level.TRACE)
        appender.start()

        DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders()
        httpHeaders.add("Authorization", "Bearer foo")
        httpHeaders.add("Proxy-Authorization", "AWS4-HMAC-SHA256 bar")
        httpHeaders.add("Cookie", "baz")
        httpHeaders.add("Set-Cookie", "qux")
        httpHeaders.add("X-Forwarded-For", "quux")
        httpHeaders.add("X-Forwarded-Host", "quuz")
        httpHeaders.add("X-Real-IP", "waldo")
        httpHeaders.add("X-Forwarded-For", "fred")
        httpHeaders.add("Credential", "foo")
        httpHeaders.add("Signature", "bar probably secret")

        def request = Mock(io.micronaut.http.HttpRequest)
        def nettyRequest = Stub(io.netty.handler.codec.http.HttpRequest) {
            headers() >> httpHeaders
        }
        ((io.micronaut.http.client.netty.DefaultHttpClient) client).traceRequest(request, nettyRequest)

        then:
        appender.events.size() == httpHeaders.size()
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
        ctx.close()
    }

    static class MemoryAppender extends AppenderBase<ILoggingEvent> {
        final BlockingQueue<String> events = new LinkedBlockingQueue<>()

        @Override
        protected void append(ILoggingEvent e) {
            events.add(e.formattedMessage)
        }
    }
}
