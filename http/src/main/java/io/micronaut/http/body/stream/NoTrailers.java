/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.http.HttpHeaders;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The trailers of a body that carries none: an immutable empty {@link HttpHeaders} and a stage
 * that is already complete with it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class NoTrailers implements HttpHeaders {
    /**
     * Empty, immutable headers.
     */
    public static final HttpHeaders HEADERS = new NoTrailers();
    /**
     * The trailers of a body that carries none: already complete with {@link #HEADERS}.
     */
    public static final CompletionStage<HttpHeaders> STAGE = CompletableFuture.completedFuture(HEADERS);

    private NoTrailers() {
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return List.of();
    }

    @Override
    public @Nullable String get(CharSequence name) {
        return null;
    }

    @Override
    public Set<String> names() {
        return Set.of();
    }

    @Override
    public Collection<List<String>> values() {
        return List.of();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return Optional.empty();
    }

    @Override
    public boolean isEmpty() {
        return true;
    }
}
