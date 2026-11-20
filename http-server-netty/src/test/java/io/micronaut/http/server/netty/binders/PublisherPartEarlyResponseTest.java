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
package io.micronaut.http.server.netty.binders;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedAttribute;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.reactivestreams.Publisher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route that answers without ever subscribing to its {@code Publisher<CompletedFileUpload>} or
 * {@code Publisher<CompletedAttribute>} argument ends the request while a part is still arriving.
 * The server keeps buffering that part, and must release it once it is complete instead of
 * leaking it. Leaks are detected by the leak presence extension on the test classpath.
 */
@Timeout(30)
class PublisherPartEarlyResponseTest {
    private static final String BOUNDARY = "bnd12345";

    @Controller("/publisher-part-early-response")
    @Requires(property = "spec.name", value = "PublisherPartEarlyResponseTest")
    static class Ctrl {
        @Post(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA)
        String file(Publisher<CompletedFileUpload> data) {
            return "early";
        }

        @Post(value = "/attribute", consumes = MediaType.MULTIPART_FORM_DATA)
        String attribute(Publisher<CompletedAttribute> data) {
            return "early";
        }
    }

    @ParameterizedTest
    @CsvSource({
        "file, true, false, false",
        "file, true, true, false",
        "file, true, false, true",
        "attribute, false, false, false",
    })
    void partCompletesAfterResponse(String route, boolean fileUpload, boolean mixed, boolean disk) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "PublisherPartEarlyResponseTest",
            "micronaut.server.port", -1,
            "micronaut.server.multipart.mixed", mixed,
            "micronaut.server.multipart.threshold", 16,
            "micronaut.server.multipart.disk", disk
        ))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            String disposition = "Content-Disposition: form-data; name=\"data\"" + (fileUpload ? "; filename=\"a.txt\"" : "");
            String first = "--" + BOUNDARY + "\r\n" +
                disposition + "\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "first-half-of-the-first-part-";
            String rest = "second-half-of-the-first-part\r\n" +
                "--" + BOUNDARY + "\r\n" +
                disposition + "\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "a-second-part-that-starts-after-the-response\r\n" +
                "--" + BOUNDARY + "--\r\n";
            byte[] firstBytes = first.getBytes(StandardCharsets.UTF_8);
            byte[] restBytes = rest.getBytes(StandardCharsets.UTF_8);

            try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();
                out.write(("POST /publisher-part-early-response/" + route + " HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Content-Type: multipart/form-data; boundary=" + BOUNDARY + "\r\n" +
                    "Content-Length: " + (firstBytes.length + restBytes.length) + "\r\n" +
                    "\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(firstBytes);
                out.flush();

                // the route answers while the first part is still incomplete
                String response = readResponse(in);
                assertTrue(response.startsWith("HTTP/1.1 200 "), response);
                assertTrue(response.endsWith("early"), response);

                // now finish the first part and send another one
                out.write(restBytes);
                // a second request on the same connection makes sure the first body was fully processed
                out.write(("GET /publisher-part-early-response/none HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "\r\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                String second = readResponse(in);
                assertTrue(second.startsWith("HTTP/1.1 "), second);
            }
        }
    }

    private static String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int contentLength = -1;
        int headerEnd = -1;
        while (true) {
            int b = in.read();
            if (b == -1) {
                break;
            }
            buf.write(b);
            String s = buf.toString(StandardCharsets.ISO_8859_1);
            if (headerEnd == -1 && s.endsWith("\r\n\r\n")) {
                headerEnd = s.length();
                for (String line : s.split("\r\n")) {
                    if (line.regionMatches(true, 0, "content-length:", 0, 15)) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }
                if (contentLength <= 0) {
                    break;
                }
            }
            if (headerEnd != -1 && s.length() - headerEnd >= contentLength) {
                break;
            }
        }
        return buf.toString(StandardCharsets.ISO_8859_1);
    }
}
