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

import java.util.concurrent.Future;

/**
 * <p>Typed event listener is one per event type publisher, allows more caching and reduces the overhead required to locate listeners.</p>
 *
 * @author Denis Stepanov
 * @since 2.5.1
 */
public interface ApplicationTypedEventPublisher<T> {

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
     */
    @NonNull
    Future<Void> publishEventAsync(@NonNull T event);
}
