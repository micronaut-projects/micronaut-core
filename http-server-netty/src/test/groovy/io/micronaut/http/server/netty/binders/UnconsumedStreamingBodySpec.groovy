package io.micronaut.http.server.netty.binders

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.multipart.PartData
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

/**
 * A streaming body argument that the route does not consume because the route fails before
 * it subscribes must not leak or stall the keep-alive connection.
 */
class UnconsumedStreamingBodySpec extends Specification {
    private static final String BOUNDARY = "----boundary"
    private static final int FIRST_LENGTH = 1000
    private static final int REST_LENGTH = 1_000_000

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': 'UnconsumedStreamingBodySpec'])

    @Shared
    EmbeddedServer server = ctx.getBean(EmbeddedServer).start()

    @Unroll
    def 'connection is not stalled by an unconsumed body: #path'(String path, boolean multipart) {
        given:
        Socket socket = new Socket("localhost", server.port)
        socket.setSoTimeout(10_000)
        OutputStream out = socket.getOutputStream()
        InputStream input = socket.getInputStream()

        when:
        out.write(head(path, multipart))
        out.flush()
        // write on another thread, the server may not read all of it
        CompletableFuture.runAsync {
            try {
                byte[] piece = ("y" * 8192).getBytes(StandardCharsets.US_ASCII)
                for (int i = 0; i < REST_LENGTH; i += piece.length) {
                    out.write(piece, 0, Math.min(piece.length, REST_LENGTH - i))
                }
                out.write(end(multipart).getBytes(StandardCharsets.US_ASCII))
                out.write("GET /unconsumed/ok HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
                out.flush()
            } catch (IOException ignored) {
            }
        }
        def first = readResponse(input)
        def second = first == null ? null : readResponse(input)

        then:
        first != null
        first[0] == 400 || first[0] == 200
        // either the next request is answered, or the server closed the connection
        second == null || (second[0] == 200 && second[1] == "ok")

        cleanup:
        socket.close()

        where:
        path                                     | multipart
        "/unconsumed/publisher-throws"           | false
        "/unconsumed/publisher-unsatisfied"      | false
        "/unconsumed/streaming-upload-throws"    | true
        "/unconsumed/streaming-upload-unsatisfied" | true
        "/unconsumed/part-publisher-throws"      | true
    }

    private static byte[] head(String path, boolean multipart) {
        String contentType
        String first
        if (multipart) {
            contentType = "multipart/form-data; boundary=" + BOUNDARY
            first = "--" + BOUNDARY + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "\r\n" +
                    ("x" * FIRST_LENGTH)
        } else {
            contentType = MediaType.APPLICATION_OCTET_STREAM
            first = "x" * FIRST_LENGTH
        }
        long contentLength = first.length() + REST_LENGTH + end(multipart).length()
        return ("POST " + path + " HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "\r\n" +
                first).getBytes(StandardCharsets.US_ASCII)
    }

    private static String end(boolean multipart) {
        return multipart ? "\r\n--" + BOUNDARY + "--\r\n" : ""
    }

    /**
     * @return [status, body], or null if the connection was closed
     */
    private static List<Object> readResponse(InputStream input) {
        String statusLine
        try {
            statusLine = readLine(input)
        } catch (EOFException | SocketException ignored) {
            return null
        }
        int status = Integer.parseInt(statusLine.split(" ")[1])
        long contentLength = 0
        for (String line = readLine(input); !line.isEmpty(); line = readLine(input)) {
            int colon = line.indexOf(':')
            if (line.substring(0, colon).trim().equalsIgnoreCase("content-length")) {
                contentLength = Long.parseLong(line.substring(colon + 1).trim())
            }
        }
        byte[] body = new byte[(int) contentLength]
        new DataInputStream(input).readFully(body)
        return [status, new String(body, StandardCharsets.UTF_8)]
    }

    private static String readLine(InputStream input) {
        StringBuilder sb = new StringBuilder()
        while (true) {
            int b = input.read()
            if (b == -1) {
                throw new EOFException()
            }
            if (b == '\n' as char) {
                int len = sb.length()
                if (len > 0 && sb.charAt(len - 1) == '\r' as char) {
                    sb.setLength(len - 1)
                }
                return sb.toString()
            }
            sb.append((char) b)
        }
    }

    @Controller("/unconsumed")
    @Requires(property = "spec.name", value = "UnconsumedStreamingBodySpec")
    static class UnconsumedController {
        @Post(uri = "/publisher-throws", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherThrows(@Body Publisher<byte[]> body) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }

        @Post(uri = "/publisher-unsatisfied", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherUnsatisfied(@Body Publisher<byte[]> body, @QueryValue String missing) {
            return "unreachable"
        }

        @Post(uri = "/streaming-upload-throws", consumes = MediaType.MULTIPART_FORM_DATA)
        String streamingUploadThrows(StreamingFileUpload file) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }

        @Post(uri = "/streaming-upload-unsatisfied", consumes = MediaType.MULTIPART_FORM_DATA)
        String streamingUploadUnsatisfied(StreamingFileUpload file, @QueryValue String missing) {
            return "unreachable"
        }

        @Post(uri = "/part-publisher-throws", consumes = MediaType.MULTIPART_FORM_DATA)
        String partPublisherThrows(@Part("file") Publisher<PartData> file) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }

        @Get(uri = "/ok", produces = MediaType.TEXT_PLAIN)
        String ok() {
            return "ok"
        }
    }
}
