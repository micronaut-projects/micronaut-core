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

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

import java.util.Set;

/**
 * What {@link DefaultBeanContext#stopRetaining(RetentionCriteria)} asks about the singletons of the context it stops,
 * for a development launcher restarting the application: the combined answer of the {@link BeanRetentionPolicy}
 * beans and of what the launcher knows about the restart.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Experimental
public interface RetentionCriteria {

    /**
     * Whether a singleton survives the restart, with what it holds.
     *
     * @param registration The singleton's registration
     * @return True to retain it
     */
    boolean retain(BeanRegistration<?> registration);

    /**
     * The configuration prefixes a change under which releases a singleton this retains, as
     * {@link BeanRetentionPolicy#observedConfigurationPrefixes(BeanRegistration)} declares them: a configuration bean
     * under one of them that the singleton holds is not retained with it, nor does it keep it from being retained.
     *
     * @param registration The retained singleton's registration
     * @return The prefixes, empty when no configuration change releases it
     */
    Set<String> observedConfigurationPrefixes(BeanRegistration<?> registration);

    /**
     * Whether the restart replaces a class, so that a retained bean bound to it would keep the stopped generation
     * of the application reachable and run its old code: a bean whose closure has a definition, an instance or a
     * prototype it owns of such a class is not retained.
     *
     * @param type The class of a definition or an instance in the closure of a retained bean
     * @return True when the class is replaced
     */
    default boolean isReplaced(Class<?> type) {
        return false;
    }
}
