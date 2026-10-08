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

import io.micronaut.context.BeanDependencyGraph;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;
import org.jspecify.annotations.NullMarked;

import java.util.Set;

/**
 * Decides which singletons survive a {@link ReloadStrategy#RESTART restart} of the application
 * context: they are not destroyed with the old context and are adopted by the new one.
 *
 * <p>Retaining a bean is for what is expensive to create and independent of the application's
 * classes: a connection pool, a client, a script engine. Policies are beans, consulted in
 * {@link Ordered order}; a bean is retained when any policy says so, and the launcher then checks
 * that the bean's own class is not stale and that, according to the {@link BeanDependencyGraph},
 * nothing it received is stale either. A retained bean is dropped, and created again by the new
 * context, when configuration under one of the prefixes the policy
 * {@link #observedConfigurationPrefixes(BeanRegistration) declares} changed.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface BeanRetentionPolicy extends Ordered {

    /**
     * Whether the given singleton survives a restart.
     *
     * @param registration The bean's registration
     * @return True to retain it
     */
    boolean retain(BeanRegistration<?> registration);

    /**
     * The configuration prefixes a change under which invalidates a retained bean this policy
     * retains, so that a changed connection URL produces a new pool.
     *
     * @param registration The retained bean's registration
     * @return The prefixes, empty if no configuration change invalidates the bean
     */
    default Set<String> observedConfigurationPrefixes(BeanRegistration<?> registration) {
        return Set.of();
    }
}
