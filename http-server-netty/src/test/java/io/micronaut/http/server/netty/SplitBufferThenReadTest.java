/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.runtime.server.EmbeddedServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A filter that buffers a split of the request body before the route reads the original body
 * gets the whole body, and the route after it too, when the body is larger than the initial
 * request window of the server, with or without {@code Expect: 100-continue}. The route reads
 * the original only after the split completed, so the split must not wait for the pace of the
 * original ({@link ByteBody.SplitBackpressureMode#FASTEST}, not the default
 * {@link ByteBody.SplitBackpressureMode#SLOWEST}).
 */
class SplitBufferThenReadTest {
    private static final String SPEC_NAME = "SplitBufferThenReadTest";
    private static final int LENGTH = 256 * 1024;

    @ParameterizedTest
    @CsvSource({
        "/split/buffered, false",
        "/split/buffered, true",
        "/split/filter-body, false",
        "/split/filter-body, true",
    })
    void aBodyBufferedThroughASplitIsStillReadByTheRoute(String path, boolean expectContinue) throws Exception {
        byte[] body = new byte[LENGTH];
        Arrays.fill(body, (byte) 'a');
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();
                out.write(("POST " + path + " HTTP/1.1\r\n"
                    + "Host: " + server.getHost() + "\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Content-Length: " + LENGTH + "\r\n"
                    + (expectContinue ? "Expect: 100-continue\r\n" : "")
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                if (expectContinue) {
                    String interim = readHead(in);
                    assertTrue(interim.startsWith("HTTP/1.1 100 "), interim);
                }
                out.write(body);
                out.flush();
                String head = readHead(in);
                assertTrue(head.startsWith("HTTP/1.1 200 "), head);
                String expected = LENGTH + "/" + LENGTH;
                assertEquals(expected, new String(in.readNBytes(expected.length()), StandardCharsets.US_ASCII));
            }
        }
    }

    /**
     * Read a response head, up to the empty line.
     */
    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            head.write(b);
            matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3)) ? matched + 1 : 0;
        }
        return head.toString(StandardCharsets.US_ASCII);
    }

    @ServerFilter("/split/buffered")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BufferingFilter {
        @RequestFilter
        CompletionStage<@Nullable HttpResponse<?>> filter(ServerHttpRequest<?> request) {
            return request.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST).buffer().thenApply(buffered -> {
                try (CloseableAvailableByteBody b = buffered) {
                    request.setAttribute("filter-length", b.length());
                }
                return null;
            });
        }
    }

    @ServerFilter("/split/filter-body")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyFilter {
        @RequestFilter
        void filter(ServerHttpRequest<?> request, @Body byte[] body) {
            request.setAttribute("filter-length", (long) body.length);
        }
    }

    @Controller("/split")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class SplitController {
        @Post(uri = "/buffered", consumes = MediaType.ALL)
        HttpResponse<String> buffered(ServerHttpRequest<?> request, @Body byte[] body) {
            return answer(request, body);
        }

        @Post(uri = "/filter-body", consumes = MediaType.ALL)
        HttpResponse<String> filterBody(ServerHttpRequest<?> request, @Body byte[] body) {
            return answer(request, body);
        }

        private static HttpResponse<String> answer(ServerHttpRequest<?> request, byte[] body) {
            long filtered = request.getAttribute("filter-length", Long.class).orElse(-1L);
            return HttpResponse.ok(filtered + "/" + body.length).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
