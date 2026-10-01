package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A blocking client that posts a body larger than the server accepts must not hang: once the server
 * has decided to reject the request it has to keep reading (and discarding) the body, or close the
 * connection, so that the client's socket write either completes or fails. A netty client does not
 * show the problem because its writes never block.
 */
class RejectedBodyDrainSpec extends Specification {

    private static final int SIZE = 5 * 1024 * 1024
    private static final String BOUNDARY = '----boundary13243'

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13243')
    void "a blocking client posting an oversized #kind body (#framing, #limit) does not hang"() {
        given:
        Map<String, Object> config = ['spec.name': 'RejectedBodyDrainSpec']
        config.put('micronaut.server.' + limit, '10KB')
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, config)
        byte[] head
        byte[] tail
        String contentType
        if (kind == 'multipart') {
            head = ("--${BOUNDARY}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8)
            tail = ("\r\n--${BOUNDARY}--\r\n").getBytes(StandardCharsets.UTF_8)
            contentType = "multipart/form-data; boundary=${BOUNDARY}"
        } else {
            head = new byte[0]
            tail = new byte[0]
            contentType = MediaType.APPLICATION_OCTET_STREAM
        }
        boolean chunked = framing == 'chunked'
        String framingHeader = chunked ? 'Transfer-Encoding: chunked' : "Content-Length: ${head.length + SIZE + tail.length}"
        Socket socket = new Socket(server.host, server.port)
        socket.soTimeout = 20_000
        OutputStream out = socket.outputStream

        when: "the whole request is written with blocking writes on another thread"
        CompletableFuture<Throwable> write = CompletableFuture.supplyAsync {
            try {
                out.write(("POST /drain/${kind} HTTP/1.1\r\nHost: localhost\r\nContent-Type: ${contentType}\r\n${framingHeader}\r\n\r\n").getBytes(StandardCharsets.UTF_8))
                writeBody(out, head, chunked)
                byte[] chunk = new byte[64 * 1024]
                int remaining = SIZE
                while (remaining > 0) {
                    int n = Math.min(chunk.length, remaining)
                    writeBody(out, n == chunk.length ? chunk : Arrays.copyOf(chunk, n), chunked)
                    remaining -= n
                }
                writeBody(out, tail, chunked)
                if (chunked) {
                    out.write('0\r\n\r\n'.getBytes(StandardCharsets.US_ASCII))
                }
                out.flush()
                return null
            } catch (IOException e) {
                // a reset from the server is fine: the client is not stuck
                return e
            }
        }
        // meanwhile read whatever the server answers
        CompletableFuture<String> response = CompletableFuture.supplyAsync {
            try {
                return new String(socket.inputStream.readNBytes(12), StandardCharsets.US_ASCII)
            } catch (IOException e) {
                return "closed: " + e.message
            }
        }

        then: "the write finishes (or fails with a reset) instead of blocking forever"
        Throwable writeResult
        try {
            writeResult = write.get(15, TimeUnit.SECONDS)
        } catch (TimeoutException e) {
            throw new AssertionError("client write still blocked, response so far: " + (response.isDone() ? response.get() : "<none>"), e)
        }
        writeResult == null || writeResult instanceof IOException

        and: "the server rejected the request"
        response.get(15, TimeUnit.SECONDS).startsWith('HTTP/1.1 413')

        cleanup:
        socket.close()
        server.stop()

        where:
        kind        | framing          | limit
        'bytes'     | 'content-length' | 'max-request-size'
        'bytes'     | 'chunked'        | 'max-request-size'
        'multipart' | 'content-length' | 'max-request-size'
        'multipart' | 'chunked'        | 'max-request-size'
        'multipart' | 'content-length' | 'multipart.max-file-size'
        'multipart' | 'chunked'        | 'multipart.max-file-size'
    }

    private static void writeBody(OutputStream out, byte[] bytes, boolean chunked) {
        if (bytes.length == 0) {
            return
        }
        if (chunked) {
            out.write((Integer.toHexString(bytes.length) + '\r\n').getBytes(StandardCharsets.US_ASCII))
            out.write(bytes)
            out.write('\r\n'.getBytes(StandardCharsets.US_ASCII))
        } else {
            out.write(bytes)
        }
    }

    @Requires(property = 'spec.name', value = 'RejectedBodyDrainSpec')
    @Controller('/drain')
    static class DrainController {
        @Post(value = '/bytes', consumes = MediaType.APPLICATION_OCTET_STREAM)
        String bytes(@Body byte[] body) {
            return "got ${body.length}"
        }

        @Post(value = '/multipart', consumes = MediaType.MULTIPART_FORM_DATA)
        String multipart(CompletedFileUpload file) {
            return "got ${file.size}"
        }
    }
}
