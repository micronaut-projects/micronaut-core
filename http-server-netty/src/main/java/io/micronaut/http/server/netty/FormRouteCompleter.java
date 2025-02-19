/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.netty.multipart.NettyCompletedFileUpload;
import io.micronaut.http.server.netty.multipart.NettyPartData;
import io.micronaut.web.router.RouteMatch;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * This class receives a stream of {@link MicronautHttpData} and splits it into individual streams
 * for each form field, and they can all be subscribed to and bound independently.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public final class FormRouteCompleter implements Subscriber<Object> {
    private static final Logger LOG = LoggerFactory.getLogger(FormRouteCompleter.class);

    private final DelayedExecutionFlow<RouteMatch<?>> execute = DelayedExecutionFlow.create();
    private final EventLoop eventLoop;
    private final EventLoopFlow flow;
    private boolean executed;
    private final RouteMatch<?> routeMatch;
    private Subscription upstreamSubscription;
    private final Set<MicronautHttpData<?>> allData = new LinkedHashSet<>();
    private final Map<String, Claimant> claimants = new HashMap<>();
    private boolean upstreamDemanded = false;

    FormRouteCompleter(RouteMatch<?> routeMatch, EventLoop eventLoop) {
        this.flow = new EventLoopFlow(eventLoop);
        this.eventLoop = eventLoop;
        this.routeMatch = routeMatch;
    }

    public DelayedExecutionFlow<RouteMatch<?>> getExecute() {
        return execute;
    }

    @Override
    public void onSubscribe(Subscription s) {
        if (flow.executeNow(() -> onSubscribe0(s))) {
            onSubscribe0(s);
        }
    }

    private void onSubscribe0(Subscription s) {
        upstreamSubscription = s;
        upstreamDemanded = true;
        s.request(1);
    }

    @Override
    public void onNext(Object o) {
        if (flow.executeNow(() -> onNext0(o))) {
            onNext0(o);
        }
    }

    private void onNext0(Object o) {
        try {
            addData((MicronautHttpData<?>) o);
        } catch (Exception e) {
            upstreamSubscription.cancel();
            onError(e);
        }
    }

    @Override
    public void onComplete() {
        if (flow.executeNow(this::onComplete0)) {
            onComplete0();
        }
    }

    private void onComplete0() {
        for (Claimant claimant : claimants.values()) {
            claimant.sink.tryEmitComplete();
        }
        if (!executed) {
            executed = true;
            execute.complete(routeMatch);
        }
    }

    @Override
    public void onError(Throwable t) {
        if (flow.executeNow(() -> onError0(t))) {
            onError0(t);
        }
    }

    private void onError0(Throwable failure) {
        for (Claimant claimant : claimants.values()) {
            claimant.sink.tryEmitError(failure);
        }
        for (Object toDiscard : routeMatch.getVariableValues().values()) {
            if (toDiscard instanceof ReferenceCounted rc) {
                rc.release();
            }
            if (toDiscard instanceof io.netty.util.ReferenceCounted rc) {
                rc.release();
            }
            if (toDiscard instanceof NettyCompletedFileUpload fu) {
                fu.discard();
            }
        }
        executed = true;
        try {
            execute.completeExceptionally(failure);
        } catch (IllegalStateException ignored) {
        }
    }

    private void addData(MicronautHttpData<?> data) {
        allData.add(data);
        upstreamDemanded = false;

        String name = data.getName();
        Claimant claimant = claimants.get(name);
        data.touch(claimant != null);
        if (claimant == null) {
            upstreamSubscription.request(1);
            return;
        }
        claimant.send(data);
        if (!executed && routeMatch.isFulfilled()) {
            executed = true;
            execute.complete(routeMatch);
        }
        if (executed) {
            if (!upstreamDemanded) {
                for (Claimant other : claimants.values()) {
                    if (other.demand > 0) {
                        upstreamDemanded = true;
                        upstreamSubscription.request(1);
                        break;
                    }
                }
            }
        } else {
            // while we still have unfulfilled parameters, request as much data as possible
            upstreamSubscription.request(1);
        }
    }

    private Claimant createClaimant(String name) {
        Claimant claimant = new Claimant();
        if (claimants.putIfAbsent(name, claimant) != null) {
            throw new IllegalStateException("Field already claimed");
        }
        return claimant;
    }

    /**
     * Claim all fields of the given name. In the returned publisher, each
     * {@link MicronautHttpData} may appear multiple times if there is new data.
     *
     * @param name The field name
     * @return The publisher of data with this field name
     */
    public Flux<? extends MicronautHttpData<?>> claimFieldsRaw(String name) {
        return createClaimant(name).flux();
    }

    /**
     * Claim all fields of the given name. When a new field of the name is seen,
     * {@code fieldFactory} is called with that field and a publisher that gets the
     * {@link PartData} every time there is new data for the field.
     *
     * @param name The field name
     * @param fieldFactory The factory to call when a new field is seen
     * @return A publisher of the objects returned by the factory
     * @param <R> The return type of the factory
     */
    public <R> Flux<R> claimFields(String name, BiFunction<? super MicronautHttpData<?>, ? super Flux<PartData>, R> fieldFactory) {
        FieldSplitter<R> proc = new FieldSplitter<>(fieldFactory);
        claimFieldsRaw(name).subscribe(proc);
        return proc.outer.asFlux();
    }

    /**
     * Claim all fields of the given name. The returned publisher will only contain fields that are
     * {@link MicronautHttpData#isCompleted() completed}.
     *
     * @param name The field name
     * @return The publisher of the complete fields
     */
    public Flux<? extends MicronautHttpData<?>> claimFieldsComplete(String name) {
        Claimant claimant = createClaimant(name);
        claimant.skipUnfinished = true;
        return claimant.flux();
    }

    public boolean isClaimed(String name) {
        return claimants.containsKey(name);
    }

    public void release() {
        for (MicronautHttpData<?> data : allData) {
            data.release();
        }
    }

    public Map<String, Object> asMap(Charset defaultCharset) {
        return toMap(defaultCharset, allData);
    }

    public static Map<String, Object> toMap(Charset charset, Collection<? extends MicronautHttpData<?>> dataList) {
        Map<String, Object> singleMap = CollectionUtils.newLinkedHashMap(dataList.size());
        Map<String, List<Object>> multiMap = new LinkedHashMap<>();
        for (MicronautHttpData<?> data : dataList) {
            String key = data.getName();
            String newValue;
            try {
                newValue = data.getString(charset);
            } catch (IOException e) {
                throw new InternalServerException("Error retrieving or decoding the value for: " + data.getName());
            }
            List<Object> multi = multiMap.get(key);
            if (multi != null) {
                multi.add(newValue);
            } else {
                Object existing = singleMap.put(key, newValue);
                if (existing != null) {
                    List<Object> combined = new ArrayList<>(2);
                    combined.add(existing);
                    combined.add(newValue);
                    singleMap.put(key, combined);
                    multiMap.put(key, combined);
                }
            }
        }
        return singleMap;
    }

    private class Claimant  {
        private final Sinks.Many<MicronautHttpData<?>> sink = Sinks.many().unicast().onBackpressureBuffer();
        private long demand;
        private MicronautHttpData<?> last;
        private MicronautHttpData<?> unsentIncomplete;
        private boolean skipUnfinished = false;

        public Flux<MicronautHttpData<?>> flux() {
            return sink.asFlux()
                .doOnRequest(this::request)
                .doOnTerminate(this::releaseNotForwarded)
                .doOnCancel(this::releaseNotForwarded);
        }

        private void request(long n) {
            // can't use serializer here
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(() -> request(n));
                return;
            }

            long newDemand = demand + n;
            if (newDemand < demand) {
                newDemand = Long.MAX_VALUE;
            }
            demand = newDemand;
            if (newDemand > 0) {
                // upstreamSubscription can be null if onSubscribe is delayed
                if (!upstreamDemanded && upstreamSubscription != null) {
                    upstreamDemanded = true;
                    upstreamSubscription.request(1);
                }
            }
        }

        public void send(MicronautHttpData<?> data) {
            if (last != data) {
                // take ownership for this claimant. FormRouteCompleter also keeps ownership for asMap
                data.retain();
                last = data;
            }

            if (skipUnfinished && !data.isCompleted()) {
                unsentIncomplete = data;
                return;
            }
            // cancel can be called by the emitNext call, so prevent release there
            unsentIncomplete = null;

            demand--;
            if (sink.tryEmitNext(data) != Sinks.EmitResult.OK) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to emit data for field {}", data.getName());
                }
            }
        }

        void releaseNotForwarded() {
            if (unsentIncomplete != null) {
                unsentIncomplete.release();
                unsentIncomplete = null;
            }
        }
    }

    private static class FieldSplitter<R> implements Subscriber<MicronautHttpData<?>> {
        final BiFunction<? super MicronautHttpData<?>, ? super Flux<PartData>, R> fieldFactory;

        Subscription upstream;
        final Sinks.Many<R> outer = Sinks.many().unicast().onBackpressureBuffer();
        MicronautHttpData<?> currentData = null;

        Sinks.Many<PartData> innerSink;
        boolean firstInner = true;

        FieldSplitter(BiFunction<? super MicronautHttpData<?>, ? super Flux<PartData>, R> fieldFactory) {
            this.fieldFactory = fieldFactory;
        }

        @Override
        public void onSubscribe(Subscription s) {
            upstream = s;
            s.request(1);
        }

        @Override
        public void onNext(MicronautHttpData<?> data) {
            if (data != currentData) {
                if (innerSink != null) {
                    innerSink.tryEmitComplete();
                }

                currentData = data;
                innerSink = Sinks.many().unicast().onBackpressureBuffer();
                firstInner = true;
                outer.tryEmitNext(fieldFactory.apply(data, innerSink.asFlux().doOnRequest(n -> {
                    if (firstInner) {
                        firstInner = false;
                        if (n != Long.MAX_VALUE) {
                            n--;
                        }
                    }
                    if (n != 0) {
                        upstream.request(n);
                    }
                })));
            }
            MicronautHttpData<?>.Chunk chunk = data.pollChunk();
            if (chunk == null) {
                upstream.request(1);
            } else {
                NettyPartData part = new NettyPartData(() -> {
                    if (data instanceof FileUpload fileUpload) {
                        return Optional.of(MediaType.of(fileUpload.getContentType()));
                    } else {
                        return Optional.empty();
                    }
                }, chunk::claim);
                innerSink.tryEmitNext(part);
            }
        }

        @Override
        public void onError(Throwable t) {
            outer.tryEmitError(t);
            if (innerSink != null) {
                innerSink.tryEmitError(t);
            }
        }

        @Override
        public void onComplete() {
            outer.tryEmitComplete();
            if (innerSink != null) {
                innerSink.tryEmitComplete();
            }
        }
    }
}
