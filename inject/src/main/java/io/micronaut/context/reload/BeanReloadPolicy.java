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
import org.jspecify.annotations.Nullable;

/**
 * Decides what happens, in a {@link ReloadStrategy#RELOAD reload}, to a bean that is not itself
 * of the changed classes but received an instance of them.
 *
 * <p>A bean that watches the set that changed needs no answer: it repairs itself from the change
 * it receives. Policies decide for the rest. They are beans, consulted in {@link Ordered order};
 * the first that answers decides. A bean no policy answers for makes the launcher fall back to a
 * {@link ReloadStrategy#RESTART restart} when its strategy is {@link ReloadStrategy#AUTO}, and fail
 * the reload when it is a hard {@link ReloadStrategy#RELOAD}. The launcher's own policy answers
 * last, from the {@link BeanDependencyGraph}: {@link Action#REINJECT} when every stale dependency
 * arrived through a field or a method, {@link Action#RECREATE} when one arrived through the
 * constructor.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface BeanReloadPolicy extends Ordered {

    /**
     * Decides for one affected bean.
     *
     * @param registration The bean's registration
     * @param event The change
     * @param graph The dependency graph, to see how the bean received what changed
     * @return The action, or null when this policy has no answer for the bean
     */
    @Nullable Action decide(BeanRegistration<?> registration, ClassChangeEvent event, BeanDependencyGraph graph);

    /**
     * What happens to an affected bean.
     */
    enum Action {
        /**
         * The bean is left as it is. Only correct when what it holds is not used again.
         */
        RETAIN,
        /**
         * The bean's field and method injection points are resolved again on the existing instance.
         */
        REINJECT,
        /**
         * The bean is destroyed and created again on demand, or eagerly if it was eager.
         */
        RECREATE,
        /**
         * The bean cannot be repaired in place: the launcher restarts the context instead.
         */
        RESTART
    }
}
