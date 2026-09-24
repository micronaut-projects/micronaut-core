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
package io.micronaut.http.reactive.execution;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The reactive execution flow.
 * NOTE: The flow is expected to produce only one result.
 *
 * @param <T> The value type
 * @author Denis Stepnov
 * @since 4.0.0
 */
@Internal
public sealed interface ReactiveExecutionFlow<T> extends ExecutionFlow<T> permits ReactorExecutionFlowImpl {

    /**
     * Creates a new reactive flow from a publisher.
     *
     * @param publisher The publisher
     * @param <K>       THe flow value type
     * @return a new flow
     */
    static <K> ReactiveExecutionFlow<K> fromPublisher(Publisher<K> publisher) {
        return (ReactiveExecutionFlow<K>) new ReactorExecutionFlowImpl(publisher);
    }

    /**
     * Creates a new reactive flow from a publisher. This method eagerly subscribes to the
     * publisher, and may return an immediate {@link ExecutionFlow} if possible. The flow has a
     * single result: the first item of a multi-valued publisher is taken and the rest is cancelled.
     * <p>The publisher is subscribed to before the returned flow is, so the Reactor context of a
     * subscriber downstream of the flow does not reach it, only the given propagated context does.
     * Use {@link #fromPublisherImmediate(Publisher)} where that context has to be preserved.
     *
     * @param publisher         The publisher
     * @param propagatedContext A context to propagate in the reactor context and as a thread-local
     *                          in the subscribe operation and while the signals are handled, so
     *                          that the steps of the flow run in it when the publisher completes on
     *                          another thread.
     * @param <K>       The flow value type
     * @return a new flow
     * @since 4.8.0
     */
    static <K> ExecutionFlow<K> fromPublisherEager(Publisher<K> publisher, PropagatedContext propagatedContext) {
        return ReactorExecutionFlowImpl.defuse(publisher, propagatedContext);
    }

    /**
     * Returns the immediate flow of a publisher that already holds its result, like
     * {@code Mono.just}, {@code Mono.error} or the publisher of a flow. Nothing is subscribed to,
     * so this is safe where the publisher must still see the Reactor context of a downstream
     * subscriber: the caller falls back to {@link #fromPublisher(Publisher)} when {@code null} is
     * returned.
     *
     * @param publisher The publisher
     * @param <K>       The flow value type
     * @return The immediate flow, or {@code null} if the publisher has to be subscribed to
     * @since 5.3.0
     */
    @Nullable
    static <K> ExecutionFlow<K> fromPublisherImmediate(Publisher<K> publisher) {
        return ReactorExecutionFlowImpl.immediate(publisher);
    }

    /**
     * Create a new reactive flow by invoking a supplier asynchronously.
     *
     * @param executor The executor
     * @param supplier The supplier
     * @param <K>      The flow value type
     * @return a new flow
     */
    static <K> ReactiveExecutionFlow<K> async(Executor executor, Supplier<ExecutionFlow<K>> supplier) {
        PropagatedContext ctx = PropagatedContext.getOrEmpty();
        Scheduler scheduler = Schedulers.fromExecutor(r -> executor.execute(ctx.wrap(r)));
        return (ReactiveExecutionFlow<K>) new ReactorExecutionFlowImpl(
            Mono.fromSupplier(supplier).flatMap(ReactorExecutionFlowImpl::toMono)
                .subscribeOn(scheduler)
        );
    }

    /**
     * Creates a new reactive flow from other flow.
     *
     * @param flow The flow
     * @param <K>  THe flow value type
     * @return a new flow
     */
    static <K> ReactiveExecutionFlow<K> fromFlow(ExecutionFlow<K> flow) {
        if (flow instanceof ReactiveExecutionFlow<K> executionFlow) {
            return executionFlow;
        }
        return (ReactiveExecutionFlow<K>) new ReactorExecutionFlowImpl(ReactorExecutionFlowImpl.toMono(flow));
    }

    /**
     * Returns the reactive flow represented by a publisher.
     *
     * @return The publisher
     */
    Publisher<T> toPublisher();

    /**
     * Convert the given flow to a reactive publisher. The supplier is called for every
     * subscription to the publisher.
     *
     * @param flowSupplier The flow supplier
     * @param <K>          The element type
     * @return The publisher
     */
    @SingleResult
    static <K> Publisher<K> toPublisher(Supplier<ExecutionFlow<K>> flowSupplier) {
        return (Publisher<K>) ReactorExecutionFlowImpl.toMono(flowSupplier);
    }

    /**
     * Convert the given flow to a reactive publisher.
     *
     * @param flow The flow
     * @param <K>  The element type
     * @return The publisher
     */
    @SingleResult
    static <K> Publisher<K> toPublisher(ExecutionFlow<K> flow) {
        return (Publisher<K>) ReactorExecutionFlowImpl.toMono(flow);
    }
}
