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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.execution.ExecutionFlow;
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
public interface ReactiveExecutionFlow<T> extends ExecutionFlow<T> {

    /**
     * Creates a new reactive flow from a publisher.
     *
     * @param publisher The publisher
     * @param <K>       THe flow value type
     * @return a new flow
     */
    @NonNull
    static <K> ReactiveExecutionFlow<K> fromPublisher(@NonNull Publisher<K> publisher) {
        return (ReactiveExecutionFlow<K>) new ReactorExecutionFlowImpl(publisher);
    }

    /**
     * Create a new reactive flow by invoking a supplier asynchronously.
     *
     * @param executor The executor
     * @param supplier The supplier
     * @param <K>      The flow value type
     * @return a new flow
     */
    @NonNull
    static <K> ReactiveExecutionFlow<K> async(@NonNull Executor executor, @NonNull Supplier<ExecutionFlow<K>> supplier) {
        Scheduler scheduler = Schedulers.fromExecutor(executor);
        return (ReactiveExecutionFlow<K>) new ReactorExecutionFlowImpl(
            Mono.fromSupplier(supplier).flatMap(ReactorExecutionFlowImpl::toMono).subscribeOn(scheduler)
        );
    }

    /**
     * Creates a new reactive flow from other flow.
     *
     * @param flow The flow
     * @param <K>  THe flow value type
     * @return a new flow
     */
    @NonNull
    static <K> ReactiveExecutionFlow<K> fromFlow(@NonNull ExecutionFlow<K> flow) {
        if (flow instanceof ReactiveExecutionFlow<K>) {
            return (ReactiveExecutionFlow<K>) flow;
        }
        return (ReactiveExecutionFlow<K>) new ReactorExecutionFlowImpl(ReactorExecutionFlowImpl.toMono(flow));
    }

    /**
     * Returns the reactive flow represented by a publisher.
     *
     * @return The publisher
     */
    @NonNull
    Publisher<T> toPublisher();

    static <K> Publisher<K> toPublisher(Supplier<ExecutionFlow<K>> flowSupplier) {
        return (Publisher<K>) ReactorExecutionFlowImpl.toMono(flowSupplier);
    }
}
