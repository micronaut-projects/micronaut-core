/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.retry;

import io.micronaut.core.annotation.Internal;
import io.micronaut.retry.intercept.DefaultRetryRunner;
import io.micronaut.retry.intercept.MutableRetryState;
import io.micronaut.retry.intercept.PolicyRetryStateBuilder;
import io.micronaut.retry.intercept.RetryEventEmitter;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Internal implementation of {@link RetryOperations} that creates a fresh retry state per execution while
 * reusing the shared retry runner infrastructure.
 */
@Internal
final class DefaultRetryOperations implements RetryOperations {

    private final RetryPolicy retryPolicy;
    private final DefaultRetryRunner retryRunner;
    private final RetryEventEmitter retryEventEmitter;

    DefaultRetryOperations(RetryPolicy retryPolicy,
                           DefaultRetryRunner retryRunner,
                           RetryEventEmitter retryEventEmitter) {
        this.retryPolicy = retryPolicy;
        this.retryRunner = retryRunner;
        this.retryEventEmitter = retryEventEmitter;
    }

    @Override
    public <T> T execute(Supplier<T> supplier) {
        return retryRunner.executeSync(supplier, newRetryState(), DefaultRetryOperations.class.getSimpleName(), retryEventEmitter);
    }

    @Override
    public <T> CompletionStage<T> executeCompletionStage(Supplier<? extends CompletionStage<T>> supplier) {
        return retryRunner.executeCompletionStage(supplier, newRetryState(), DefaultRetryOperations.class.getSimpleName(), retryEventEmitter);
    }

    @Override
    public <T> Publisher<T> executePublisher(Supplier<? extends Publisher<T>> supplier) {
        return retryRunner.executePublisher(supplier, newRetryState(), DefaultRetryOperations.class.getSimpleName(), retryEventEmitter);
    }

    private MutableRetryState newRetryState() {
        return (MutableRetryState) new PolicyRetryStateBuilder(retryPolicy).build();
    }
}
