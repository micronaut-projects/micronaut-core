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
package io.micronaut.inject;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.BeanContext;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.BeanResolutionContext;

/**
 * Interface for other types that are conditional within a context.
 *
 * @author graemerocher
 * @since 1.0
 */
@FunctionalInterface
public interface BeanContextConditional {

    /**
     * Return whether this component is enabled for the given context.
     *
     * @param context The context
     * @return True if it is
     */
    default boolean isEnabled(@NonNull BeanContext context) {
        return isEnabled(context, null);
    }

    /**
     * Return whether this component is enabled for the given context.
     *
     * @param context The context
     * @param resolutionContext The bean resolution context
     * @return True if it is
     */
    boolean isEnabled(@NonNull BeanContext context, @Nullable BeanResolutionContext resolutionContext);
}
