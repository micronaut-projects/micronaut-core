/*
 * Copyright 2017-2021 original authors
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

import java.util.Optional;

/**
 * Interface for classes that can supply {@link ApplicationTypedEventPublisher}.
 *
 * @author Denis Stepanov
 * @since 2.5.1
 */
public interface ApplicationTypedEventPublisherProvider {

    /**
     * Find an event publisher that corresponds to the event type class.
     * If there is no listeners empty optional is returned.
     *
     * @param eventType The event type class
     * @param <T>       The event type
     * @return The event publisher or empty
     * @since 2.5.1
     */
    <T> Optional<ApplicationTypedEventPublisher<T>> findTypedEventPublisher(Class<T> eventType);

}
