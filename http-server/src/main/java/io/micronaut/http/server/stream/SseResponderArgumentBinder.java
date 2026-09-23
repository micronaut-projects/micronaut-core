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

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.bind.binders.PostponedRequestArgumentBinder;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.binding.ServerRequestBody;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.builder.SseResponder;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Binds the {@link SseResponder} of a server-sent events route, which creates its emitter. Bound
 * after the filters, so the emitter sees the request the filters continued with.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class SseResponderArgumentBinder implements TypedRequestArgumentBinder<SseResponder>, PostponedRequestArgumentBinder<SseResponder> {

    private static final Argument<SseResponder> ARGUMENT = Argument.of(SseResponder.class);

    private final BeanProvider<MessageBodyHandlerRegistry> bodyHandlerRegistry;
    private final BeanProvider<TaskScheduler> scheduler;
    private final HttpServerConfiguration serverConfiguration;
    private final ConversionService conversionService;

    SseResponderArgumentBinder(BeanProvider<MessageBodyHandlerRegistry> bodyHandlerRegistry,
                               @Named(TaskExecutors.SCHEDULED) BeanProvider<TaskScheduler> scheduler,
                               HttpServerConfiguration serverConfiguration,
                               ConversionService conversionService) {
        this.bodyHandlerRegistry = bodyHandlerRegistry;
        this.scheduler = scheduler;
        this.serverConfiguration = serverConfiguration;
        this.conversionService = conversionService;
    }

    @Override
    public Argument<SseResponder> argumentType() {
        return ARGUMENT;
    }

    @Override
    public BindingResult<SseResponder> bind(ArgumentConversionContext<SseResponder> context, HttpRequest<?> source) {
        ServerHttpRequest<?> server = ServerRequestBody.of(source);
        if (server == null) {
            return BindingResult.unsatisfied();
        }
        // the route ran on this executor: a blocking handler runs as a new task on it, so that
        // the response is sent while the handler still runs
        RouteInfo<?> routeInfo = RouteAttributes.getRouteInfo(source).orElse(null);
        Executor executor = routeInfo == null ? null : routeInfo.getExecutor(serverConfiguration.getThreadSelection());
        SseResponder responder = new DefaultSseEmitter(source, server.byteBodyFactory(), this, executor);
        return () -> Optional.of(responder);
    }

    /**
     * @return The message body writers, which encode the data of the events
     */
    MessageBodyHandlerRegistry bodyHandlerRegistry() {
        return bodyHandlerRegistry.get();
    }

    /**
     * @return The scheduler of the heartbeats
     */
    TaskScheduler scheduler() {
        return scheduler.get();
    }

    /**
     * @return The conversion service
     */
    ConversionService conversionService() {
        return conversionService;
    }
}
