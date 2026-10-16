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
package io.micronaut.context.reload;

import io.micronaut.context.DefaultBeanContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

/**
 * A context that can stop {@link DefaultBeanContext#stopRetaining(RetentionCriteria) retaining} some of its
 * singletons for the next one, so that what its stop shuts down, such as the graceful shutdown that the
 * {@link io.micronaut.context.event.ShutdownEvent} starts, leaves those running.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Experimental
public interface RetainingBeanContext {

    /**
     * Whether the stop in progress keeps the given instance alive for the context that comes next.
     *
     * @param bean The instance
     * @return True when a retaining stop is in progress and retains it
     */
    boolean isRetainedOnStop(Object bean);
}
