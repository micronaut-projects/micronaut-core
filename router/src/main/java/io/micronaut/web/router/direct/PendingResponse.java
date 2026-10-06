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
package io.micronaut.web.router.direct;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.body.stream.NoTrailers;

import java.util.Map;
import java.util.Optional;

/**
 * {@link DirectRouteLookup#PENDING}: the marker of a request an asynchronous direct route
 * matched, compared by identity. It is immutable, and a server runtime never writes it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class PendingResponse implements HttpResponse<Object> {

    static final PendingResponse INSTANCE = new PendingResponse();

    private final MutableConvertibleValues<Object> attributes = new MutableConvertibleValuesMap<>(Map.of());

    private PendingResponse() {
    }

    @Override
    public int code() {
        return HttpStatus.PROCESSING.getCode();
    }

    @Override
    public String reason() {
        return "Pending direct route";
    }

    @Override
    public HttpHeaders getHeaders() {
        return NoTrailers.HEADERS;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        // Map.of: immutable
        return attributes;
    }

    @Override
    public Optional<Object> getBody() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "DirectRouteLookup.PENDING";
    }
}
