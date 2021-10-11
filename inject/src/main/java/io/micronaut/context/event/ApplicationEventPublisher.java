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
package io.micronaut.context.event;

import io.micronaut.core.annotation.NonNull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * <p>Interface for classes that publish events received by {@link ApplicationEventListener} instances.</p>
 * <p>
 * <p>Note that this interface is designed for application level, non-blocking synchronous events for decoupling code
 * and is not a replacement for a messaging system</p>
 *
 * @author Graeme Rocher
 * @param <T> The event type
 * @since 1.0
 */
public interface ApplicationEventPublisher<T> {

    ApplicationEventPublisher<?> NO_OP = new NoOpApplicationEventPublisher();

    /**
     * Returns a no-op instance of {@link ApplicationEventPublisher}.
     *
     * @param <K> The event type
     * @return an instance of {@link ApplicationEventPublisher}
     */
    static <K> ApplicationEventPublisher<K> noOp() {
        return (ApplicationEventPublisher<K>) NO_OP;
    }

    /**
     * Publish the given event. The event will be published synchronously and only return once all listeners have consumed the event.
     *
     * @param event The event to publish
     */
    void publishEvent(@NonNull T event);

    /**
     * Publish the given event. The event will be published asynchronously. A future is returned that can be used to check whether the event completed successfully or not.
     *
     * @param event The event to publish
     * @return A future that completes when the event is published
     * @since 1.3.5
     */
    default @NonNull Future<Void> publishEventAsync(@NonNull T event) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("Asynchronous event publishing is not supported by this implementation"));
        return future;
    }

}
