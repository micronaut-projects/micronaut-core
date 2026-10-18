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
package io.micronaut.http.server.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.bind.binders.PostponedRequestArgumentBinder;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.server.binding.ServerRequestBody;
import io.micronaut.web.router.builder.HandlerMethod;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Binds the {@link HandlerMethod.SseResponder} of a server-sent events route, which starts its
 * stream. Bound after the filters, so the stream sees the request the filters continued with.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class SseResponderArgumentBinder implements TypedRequestArgumentBinder<HandlerMethod.SseResponder>, PostponedRequestArgumentBinder<HandlerMethod.SseResponder> {

    private static final Argument<HandlerMethod.SseResponder> ARGUMENT = Argument.of(HandlerMethod.SseResponder.class);

    private final SseEmitterFactory emitters;

    SseResponderArgumentBinder(SseEmitterFactory emitters) {
        this.emitters = emitters;
    }

    @Override
    public Argument<HandlerMethod.SseResponder> argumentType() {
        return ARGUMENT;
    }

    @Override
    public BindingResult<HandlerMethod.SseResponder> bind(ArgumentConversionContext<HandlerMethod.SseResponder> context, HttpRequest<?> source) {
        ServerHttpRequest<?> server = ServerRequestBody.of(source);
        if (server == null) {
            return BindingResult.unsatisfied();
        }
        ByteBodyFactory bodyFactory = server.byteBodyFactory();
        HandlerMethod.SseResponder responder = handler -> emitters.start(source, bodyFactory, handler);
        return () -> Optional.of(responder);
    }
}
