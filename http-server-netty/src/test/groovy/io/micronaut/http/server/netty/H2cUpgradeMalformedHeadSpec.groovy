package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * An h2c upgrade of a request head that Netty's decoder rejected must not reach routes.
 */
class H2cUpgradeMalformedHeadSpec extends Specification {

    static final AtomicInteger INVOCATIONS = new AtomicInteger()

    void 'h2c upgrade of a malformed head never reaches the route (legacy=#legacy, #name)'() {
        given:
        INVOCATIONS.set(0)
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name'                                      : 'H2cUpgradeMalformedHeadSpec',
                'micronaut.server.port'                          : -1,
                'micronaut.server.http-version'                  : '2.0',
                'micronaut.server.netty.legacy-multiplex-handlers': legacy,
        ])
        EmbeddedServer server = ctx.getBean(EmbeddedServer)
        server.start()

        when:
        Socket socket = new Socket('127.0.0.1', server.port)
        socket.soTimeout = 1500
        socket.outputStream.write(("GET /h2c-malformed HTTP/1.1\r\nHost: localhost\r\n" + badLine +
                "Connection: Upgrade, HTTP2-Settings\r\nUpgrade: h2c\r\nHTTP2-Settings: AAMAAABkAAQAAP__\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
        socket.outputStream.flush()
        Thread.sleep(300)
        try {
            socket.outputStream.write('PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n'.getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.write([0, 0, 0, 4, 0, 0, 0, 0, 0] as byte[]) // empty SETTINGS
            socket.outputStream.flush()
        } catch (IOException ignored) {
        }
        readQuietly(socket.inputStream)
        socket.close()

        then:
        INVOCATIONS.get() == 0

        cleanup:
        ctx.close()

        where:
        legacy | name                     | badLine
        true   | 'invalid header name'    | 'Bad Name: x\r\nX-After: 1\r\n'
        true   | 'duplicate content-len'  | 'Content-Length: 0\r\nContent-Length: 1\r\n'
        false  | 'invalid header name'    | 'Bad Name: x\r\nX-After: 1\r\n'
        false  | 'duplicate content-len'  | 'Content-Length: 0\r\nContent-Length: 1\r\n'
    }

    private static void readQuietly(InputStream input) {
        byte[] buf = new byte[8192]
        try {
            while (input.read(buf) != -1) {
            }
        } catch (IOException ignored) {
        }
    }

    @Controller('/h2c-malformed')
    @Requires(property = 'spec.name', value = 'H2cUpgradeMalformedHeadSpec')
    static class Ctrl {
        @Get
        String get() {
            INVOCATIONS.incrementAndGet()
            'route'
        }
    }
}
