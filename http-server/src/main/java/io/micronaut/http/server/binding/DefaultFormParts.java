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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.web.router.FormPart;
import io.micronaut.web.router.FormParts;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * The {@link FormParts} of a request: its raw form fields, handed to the consumer one at a time.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultFormParts implements FormParts {
    private final FormCapableHttpRequest<?> request;
    private final FormFactory formFactory;
    private final AtomicBoolean consumed = new AtomicBoolean();

    DefaultFormParts(FormCapableHttpRequest<?> request, FormFactory formFactory) {
        this.request = request;
        this.formFactory = formFactory;
    }

    @Override
    public CompletionStage<Void> forEach(Function<? super FormPart, ? extends CompletionStage<?>> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (!consumed.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("The form parts were already consumed"));
        }
        // one part at a time: the next part is read when the consumer is done with the previous one
        return Flux.from(request.getRawFormFields())
            .concatMap(field -> {
                DefaultFormPart part = new DefaultFormPart(field, formFactory, request.getCharacterEncoding());
                CompletionStage<?> stage;
                try {
                    stage = Objects.requireNonNull(consumer.apply(part), "The consumer returned no stage");
                } catch (Throwable e) {
                    part.discardIfUnread();
                    return Mono.error(e);
                }
                return Mono.fromCompletionStage(stage).then().doFinally(signal -> part.discardIfUnread());
            }, 1)
            .then()
            .toFuture();
    }
}
