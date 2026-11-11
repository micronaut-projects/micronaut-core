package io.micronaut.http.server.netty.handler.accesslog.element

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets

class AccessLogControlCharacterSpec extends Specification {

    void "the access log escapes control characters of the request target"() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger('AccessLogControlCharacterSpec')
        logger.level = Level.INFO
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        logger.addAppender(appender)
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'AccessLogControlCharacterSpec',
                'micronaut.server.netty.access-logger.enabled': true,
                'micronaut.server.netty.access-logger.logger-name': 'AccessLogControlCharacterSpec',
                'micronaut.server.netty.access-logger.log-format': '%r',
        ])

        when:
        try (Socket socket = new Socket('127.0.0.1', server.port)) {
            socket.soTimeout = 5000
            // C1 controls: Netty 4.1.140 rejects a request target with a C0 control or DEL before it is logged
            socket.outputStream.write("GET /hello\u009b31mRED\u009b0m\u0085x HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
            socket.outputStream.flush()
            socket.inputStream.bytes
        }

        then:
        new PollingConditions(timeout: 5).eventually {
            assert appender.list.any { it.formattedMessage.contains('/hello') }
        }
        def line = appender.list.find { it.formattedMessage.contains('/hello') }.formattedMessage
        line == 'GET /hello\\x9b31mRED\\x9b0m\\x85x HTTP/1.1'

        cleanup:
        logger.detachAppender(appender)
        server.close()
    }

    void "the access log has no raw C0 control characters of a request target the decoder rejects"() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger('AccessLogControlCharacterSpec')
        logger.level = Level.INFO
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        logger.addAppender(appender)
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'AccessLogControlCharacterSpec',
                'micronaut.server.netty.access-logger.enabled': true,
                'micronaut.server.netty.access-logger.logger-name': 'AccessLogControlCharacterSpec',
                'micronaut.server.netty.access-logger.log-format': '%r',
        ])

        when:
        try (Socket socket = new Socket('127.0.0.1', server.port)) {
            socket.soTimeout = 5000
            socket.outputStream.write("GET /hello\u001b[31mRED\u001b[0m\u0001x HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
            socket.outputStream.flush()
            socket.inputStream.bytes
        }

        then:
        new PollingConditions(timeout: 5).eventually {
            assert !appender.list.empty
        }
        appender.list.every { !it.formattedMessage.contains('\u001b') && !it.formattedMessage.contains('\u0001') }

        cleanup:
        logger.detachAppender(appender)
        server.close()
    }

    void "control characters are escaped"() {
        expect:
        AccessLog.escape(value) == escaped

        where:
        value              | escaped
        'a\u001bb'        | 'a\\x1bb'
        'a\r\nb'         | 'a\\x0d\\x0ab'
        'a\u007fb\u0085c' | 'a\\x7fb\\x85c'
        null               | null
    }

    void "printable values are logged unchanged"() {
        expect:
        AccessLog.escape(value).is(value)

        where:
        value << ['/hello?a=b&c=%20d', 'Mozilla/5.0 (X11; Linux) é', '']
    }
}
