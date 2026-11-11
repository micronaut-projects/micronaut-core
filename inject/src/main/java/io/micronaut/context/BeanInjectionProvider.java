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
package io.micronaut.context;

import io.micronaut.context.annotation.ResolveWith;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

/**
 * Supplies the value of an injection point annotated with {@link ResolveWith}.
 *
 * <p>The provider is itself a bean, resolved in the active context. Its invocation retains the requesting
 * injection point on the resolution path. Resolve context-managed values through the supplied resolution
 * context so that their scopes and dependent ownership are preserved. An arbitrary value constructed by the
 * provider is not automatically given a bean lifecycle. Do not retain the active resolution context after
 * this method returns.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface BeanInjectionProvider {

    /**
     * Resolves the full declared injection type, including its generic arguments and metadata.
     *
     * @param resolutionContext The active resolution context, with the requesting path already established
     * @param argument The declared injection argument
     * @param qualifier The resolved qualifier, or {@code null} for an unqualified request
     * @param nullable Whether this injection point permits a {@code null} result
     * @param <T> The requested type
     * @return The injection value; {@code null} only when nullable is true
     */
    <T> @Nullable T get(BeanResolutionContext resolutionContext, Argument<T> argument,
                        @Nullable Qualifier<T> qualifier, boolean nullable);
}
