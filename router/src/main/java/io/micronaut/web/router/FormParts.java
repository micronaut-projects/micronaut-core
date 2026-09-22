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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Experimental;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * The parts of a submitted form, {@code application/x-www-form-urlencoded} or
 * {@code multipart/form-data}, read as they arrive:
 *
 * <pre>{@code
 * routes.handleFormStream(HttpMethod.POST, "/upload", (request, pathVariables, parts) ->
 *     parts.forEach(part -> part.isFile()
 *             ? part.transferTo(uploads.resolve(part.fileName()))
 *             : part.text().thenAccept(value -> fields.put(part.name(), value)))
 *         .thenApply(done -> HttpResponse.ok()));
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface FormParts {

    /**
     * Consume the parts of the form in the order they arrive. The next part is read when the stage
     * returned for the previous one completes, and the content a consumer did not read is
     * discarded. The form can be consumed once.
     *
     * @param consumer Consumes a part, completing when it is done with it
     * @return Completes when every part is consumed, or exceptionally when reading the form or a
     * consumer fails
     */
    CompletionStage<Void> forEach(Function<? super FormPart, ? extends CompletionStage<?>> consumer);

    /**
     * Consume only the first part with the given name; every other part is discarded as it
     * arrives, without buffering. Like {@link #forEach(Function)}, the stage completes when the
     * whole form was read:
     *
     * <pre>{@code
     * parts.part("avatar", part -> part.transferTo(path))
     *     .thenApply(found -> found ? HttpResponse.noContent() : HttpResponse.badRequest());
     * }</pre>
     *
     * @param name     The name of the part
     * @param consumer Consumes the part, completing when it is done with it
     * @return Completes with {@code true} if the form had the part, {@code false} otherwise, or
     * exceptionally when reading the form or the consumer fails
     */
    default CompletionStage<Boolean> part(String name, Function<? super FormPart, ? extends CompletionStage<?>> consumer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(consumer, "consumer");
        AtomicBoolean found = new AtomicBoolean();
        return forEach(part -> {
            if (name.equals(part.name()) && found.compareAndSet(false, true)) {
                return consumer.apply(part);
            }
            return CompletableFuture.completedFuture(null);
        }).thenApply(done -> found.get());
    }
}
