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
package io.micronaut.http.server.netty.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The text of an {@link AsyncRequestBody} is decoded like a {@code @Body String}: in the charset
 * of the content type, else the default charset. {@code Accept-Charset} is about the response.
 */
class AsyncRequestBodyTextCharsetTest {
    private static final String SPEC_NAME = "AsyncRequestBodyTextCharsetTest";
    private static final String TEXT_SEEN = "textSeen";

    @Test
    void textIgnoresAcceptCharset() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            String plain = client.toBlocking().retrieve(HttpRequest.POST("/text-charset", "hello world")
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header(HttpHeaders.ACCEPT_CHARSET, "UTF-16"), String.class);
            assertEquals("hello world|hello world", plain);

            String named = client.toBlocking().retrieve(HttpRequest.POST("/text-charset", "héllo".getBytes(StandardCharsets.ISO_8859_1))
                .contentType(new MediaType("text/plain;charset=ISO-8859-1"))
                .header(HttpHeaders.ACCEPT_CHARSET, "UTF-16"), String.class);
            assertEquals("héllo|héllo", named);
        }
    }

    @ServerFilter("/text-charset")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TextFilter {
        @RequestFilter
        CompletionStage<@Nullable HttpResponse<?>> read(HttpRequest<?> request, AsyncRequestBody body) {
            return body.copy().text().thenApply(text -> {
                request.setAttribute(TEXT_SEEN, text);
                return null;
            });
        }
    }

    @Controller("/text-charset")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TextController {
        @Post(consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN + ";charset=UTF-8")
        String receive(HttpRequest<?> request, @Body String body) {
            return request.getAttribute(TEXT_SEEN, String.class).orElse("none") + "|" + body;
        }
    }
}
