package io.micronaut.http.server.netty.binders

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.PartData
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.http.server.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * A route that returns without ever subscribing to its streaming body argument must not leak the
 * body or leave the keep-alive connection stuck. A body it subscribed to is left alone.
 */
class UnsubscribedBodyAfterResponseSpec extends Specification {
    static volatile CompletableFuture<Long> background = new CompletableFuture<>()

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run([
            'spec.name'                              : 'UnsubscribedBodyAfterResponseSpec',
            'micronaut.server.max-request-size'      : 1024 * 1024,
            'micronaut.server.multipart.max-file-size': 400 * 1024
    ])

    @Shared
    EmbeddedServer server = ctx.getBean(EmbeddedServer).start()

    @Unroll
    def 'keep-alive connection stays usable: #path, #size bytes'(String path, int size) {
        given:
        boolean multipart = !path.contains("bytes") && !path.contains("input-stream")
        byte[] file = new byte[size]
        new Random(1).nextBytes(file)
        ByteArrayOutputStream body = new ByteArrayOutputStream()
        if (multipart) {
            body.write("--bb\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f.bin\"\r\n\r\n".getBytes(StandardCharsets.UTF_8))
            body.write(file)
            body.write("\r\n--bb--\r\n".getBytes(StandardCharsets.UTF_8))
        } else {
            body.write(file)
        }
        String json = '{"a":"x"}'
        Socket s = new Socket("localhost", server.port)
        s.setSoTimeout(5000)
        OutputStream out = s.getOutputStream()
        InputStream input = s.getInputStream()

        when:
        out.write(("POST " + path + " HTTP/1.1\r\nHost: x\r\nContent-Type: " + (multipart ? "multipart/form-data; boundary=bb" : "application/octet-stream")
                + "\r\nContent-Length: " + body.size() + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
        out.write(body.toByteArray())
        out.flush()
        String first = readResponse(input)
        out.write(("POST /unsubscribed-after/json HTTP/1.1\r\nHost: x\r\nContent-Type: application/json\r\nContent-Length: " + json.length() + "\r\n\r\n" + json).getBytes(StandardCharsets.US_ASCII))
        out.flush()
        String second = readResponse(input)

        then:
        first == "200 ok"
        second == "200 json"

        cleanup:
        s.close()

        where:
        [path, size] << [[
                "/unsubscribed-after/part-data",
                "/unsubscribed-after/multipart-body",
                "/unsubscribed-after/bytes",
                "/unsubscribed-after/input-stream",
                "/unsubscribed-after/streaming-upload",
        ], [1000, 150_000, 300_000]].combinations()
    }

    def 'a body the route subscribed to is left alone'() {
        given:
        int size = 300_000
        byte[] file = new byte[size]
        new Random(1).nextBytes(file)
        background = new CompletableFuture<>()
        Socket s = new Socket("localhost", server.port)
        s.setSoTimeout(5000)
        OutputStream out = s.getOutputStream()
        InputStream input = s.getInputStream()

        when:
        byte[] head = "--bb\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f.bin\"\r\n\r\n".getBytes(StandardCharsets.UTF_8)
        byte[] tail = "\r\n--bb--\r\n".getBytes(StandardCharsets.UTF_8)
        out.write(("POST /unsubscribed-after/part-data-background HTTP/1.1\r\nHost: x\r\nContent-Type: multipart/form-data; boundary=bb\r\nContent-Length: " + (head.length + size + tail.length) + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
        out.write(head)
        out.write(file, 0, 1000)
        out.flush()
        // the route returns once it has the field, before the rest of the body is sent
        String first = readResponse(input)
        out.write(file, 1000, size - 1000)
        out.write(tail)
        out.flush()

        then:
        first == "200 ok"
        // the subscription the route made before returning is not discarded, it completes
        // normally instead of failing with the discard error
        background.get(5, TimeUnit.SECONDS) > 0

        cleanup:
        s.close()
    }

    private static String readResponse(InputStream input) {
        StringBuilder head = new StringBuilder()
        try {
            while (!head.toString().endsWith("\r\n\r\n")) {
                int b = input.read()
                if (b < 0) {
                    return head.length() == 0 ? "EOF" : "EOF in head " + head
                }
                head.append((char) b)
            }
        } catch (SocketTimeoutException ignored) {
            return "TIMEOUT"
        }
        String h = head.toString()
        int len = 0
        for (String l : h.split("\r\n")) {
            if (l.toLowerCase().startsWith("content-length:")) {
                len = Integer.parseInt(l.substring(15).trim())
            }
        }
        byte[] body = input.readNBytes(len)
        return h.substring(9, h.indexOf("\r\n") - 3).trim() + " " + new String(body, StandardCharsets.UTF_8)
    }

    @Controller("/unsubscribed-after")
    @Requires(property = "spec.name", value = "UnsubscribedBodyAfterResponseSpec")
    static class Ctrl {
        @Post(value = "/part-data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String partData(@Part("file") Publisher<PartData> file) {
            return "ok"
        }

        @Post(value = "/multipart-body", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String multipartBody(@Body MultipartBody body) {
            return "ok"
        }

        @Post(value = "/bytes", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String bytes(@Body Publisher<byte[]> body) {
            return "ok"
        }

        @Post(value = "/input-stream", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String inputStream(@Body InputStream body) {
            return "ok"
        }

        @Post(value = "/streaming-upload", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String streamingUpload(StreamingFileUpload file) {
            return "ok"
        }

        @Post(value = "/part-data-background", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String partDataBackground(@Part("file") Publisher<PartData> file) {
            Flux.from(file).map { PartData pd -> (long) pd.getBytes().length }.reduce(0L, { a, b -> a + b })
                    .subscribe({ background.complete(it) }, { background.completeExceptionally(it) })
            return "ok"
        }

        @Post(value = "/json", consumes = MediaType.APPLICATION_JSON, produces = MediaType.TEXT_PLAIN)
        String json(@Body Map<String, Object> body) {
            return "json"
        }
    }
}
