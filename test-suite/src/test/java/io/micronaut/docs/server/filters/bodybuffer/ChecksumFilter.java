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
package io.micronaut.docs.server.filters.bodybuffer;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.server.annotation.PreMatching;
import org.jspecify.annotations.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.CompletionStage;
// end::imports[]

@Requires(property = "spec.name", value = "ChecksumFilterSpec")
// tag::clazz[]
@ServerFilter("/checksum")
public class ChecksumFilter {
    @RequestFilter
    @PreMatching
    public CompletionStage<@Nullable HttpResponse<?>> verify(HttpRequest<?> request) {
        String expected = request.getHeaders().get("X-Content-SHA256");
        return AsyncRequestBody.of(request) // <1>
            .copy()
            .bytes(1024 * 1024) // <2>
            .thenApply(bytes -> sha256(bytes).equals(expected)
                ? null // <3>
                : HttpResponse.badRequest("Checksum mismatch")); // <4>
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
// end::clazz[]
