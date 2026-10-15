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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.CaseInsensitiveMutableHttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.builder.HandlerMethod;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Creates the {@link DefaultSseEmitter} of a server-sent events route when the route runs, and
 * holds what the emitters share: the writer of the events, the scheduler of the heartbeats and
 * the configuration.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class SseEmitterFactory {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Argument<Event<?>> EVENT = (Argument) Argument.of(Event.class);

    private final BeanProvider<TaskScheduler> scheduler;
    private final HttpServerConfiguration serverConfiguration;
    private final ConversionService conversionService;
    /**
     * The {@code text/event-stream} writer of the events, looked up once.
     */
    private final Supplier<MessageBodyWriter<Event<?>>> eventWriter;

    SseEmitterFactory(BeanProvider<MessageBodyHandlerRegistry> bodyHandlerRegistry,
                      @Named(TaskExecutors.SCHEDULED) BeanProvider<TaskScheduler> scheduler,
                      HttpServerConfiguration serverConfiguration,
                      ConversionService conversionService) {
        this.eventWriter = SupplierUtil.memoized(() -> bodyHandlerRegistry.get().getWriter(EVENT, List.of(MediaType.TEXT_EVENT_STREAM_TYPE)));
        this.scheduler = scheduler;
        this.serverConfiguration = serverConfiguration;
        this.conversionService = conversionService;
    }

    /**
     * Start the event stream of a route: create its emitter, and run the handler, as a new task
     * on the executor of the route if it has one, so that the response is sent while a blocking
     * handler still runs.
     *
     * @param request     The request the route runs with
     * @param bodyFactory The body factory of the response
     * @param handler     Runs the handler of the route
     * @return Completes with the response when the first event is sent or the stream ends, or
     * exceptionally when the stream fails before
     */
    CompletionStage<HttpResponse<?>> start(HttpRequest<?> request, ByteBodyFactory bodyFactory, HandlerMethod.SseResponder.Handler handler) {
        RouteInfo<?> routeInfo = RouteAttributes.getRouteInfo(request).orElse(null);
        Executor executor = routeInfo == null ? null : routeInfo.getExecutor(serverConfiguration.getThreadSelection());
        return new DefaultSseEmitter(request, bodyFactory, this).start(handler, executor);
    }

    /**
     * Encode an event like the {@code text/event-stream} writer encodes the events of a
     * {@code Publisher<Event>} body.
     *
     * @param bodyFactory The body factory of the response
     * @param event       The event
     * @return The bytes of the event
     */
    ReadBuffer encode(ByteBodyFactory bodyFactory, Event<?> event) {
        MessageBodyWriter<Event<?>> writer = eventWriter.get();
        return bodyFactory.readBufferFactory().buffer(out -> writer.writeTo(
            EVENT,
            MediaType.TEXT_EVENT_STREAM_TYPE,
            event,
            new CaseInsensitiveMutableHttpHeaders(conversionService),
            out
        ));
    }

    /**
     * @return The scheduler of the heartbeats
     */
    TaskScheduler scheduler() {
        return scheduler.get();
    }

    /**
     * @return The high-water mark of a stream
     */
    int highWaterMark() {
        return serverConfiguration.getResponseStream().getHighWaterMark();
    }

    /**
     * @return The default heartbeat period, or {@code null} for none
     */
    @Nullable Duration heartbeat() {
        return serverConfiguration.getResponseStream().getSseHeartbeat();
    }
}
