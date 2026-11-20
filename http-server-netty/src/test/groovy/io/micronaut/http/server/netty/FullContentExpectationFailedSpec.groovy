package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * In FULL_CONTENT mode, the responses the aggregator writes itself must not let a non-reading
 * client be read without bound.
 */
class FullContentExpectationFailedSpec extends Specification {

    void "a non-reading client pipelining Expect requests is not read without bound"(String serverType) {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'FullContentExpectationFailedSpec',
                'micronaut.server.netty.server-type': serverType,
        ])
        byte[] one = "GET /expect-pipelined HTTP/1.1\r\nHost: a\r\nExpect: x\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
        int count = 100_000 // ~4.7 MB, far more than the socket buffers hold
        Socket socket = new Socket()
        socket.receiveBufferSize = 4096
        socket.connect(new InetSocketAddress(server.host, server.port))

        when: "the client writes without ever reading"
        def write = CompletableFuture.runAsync {
            def out = new BufferedOutputStream(socket.outputStream, 64 * 1024)
            count.times { out.write(one) }
            out.flush()
        }
        boolean allConsumed
        try {
            write.get(15, TimeUnit.SECONDS)
            allConsumed = true
        } catch (TimeoutException ignored) {
            allConsumed = false // reads were paused
        } catch (Exception ignored) {
            allConsumed = false // connection closed
        }

        then: "the server stops reading (or closes) instead of buffering every response"
        !allConsumed

        cleanup:
        socket?.close()
        server?.close()

        where:
        serverType << ['STREAMED', 'FULL_CONTENT']
    }
}
