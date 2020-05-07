/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.event;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * <p>Interface for classes that publish events received by {@link ApplicationEventListener} instances.</p>
 * <p>
 * <p>Note that this interface is designed for application level, non-blocking synchronous events for decoupling code
 * and is not a replacement for a messaging system</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ApplicationEventPublisher {

    /**
     * Publish the given event. The event will be published synchronously and only return once all listeners have consumed the event.
     *
     * @param event The event to publish
     */
    void publishEvent(@Nonnull Object event);

    /**
     * Publish the given event. The event will be published synchronously and only return once all listeners have consumed the event.
     *
     * @param event The event to publish
     * @return A future that completes when the event is published
     */
    default Future<Void> publishEventAsync(@Nonnull Object event) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            publishEvent(event);
            future.complete(null);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
        return future;
    }
}
