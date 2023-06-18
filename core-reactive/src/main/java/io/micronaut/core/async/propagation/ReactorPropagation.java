/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.core.async.propagation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reactor propagation of {@link PropagatedContext}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class ReactorPropagation {

    /**
     * The key to be used in the Reactor context.
     */
    public static final String PROPAGATED_CONTEXT_REACTOR_CONTEXT_VIEW_KEY = "micronaut.propagated.context";

    private ReactorPropagation() {
    }

    /**
     * Adds propagated context to the Reactors' context.
     * @param context The context to be extended
     * @param propagatedContext The propagated context
     * @return The modified context
     */
    @NonNull
    public static Context addPropagatedContext(@NonNull Context context, @NonNull PropagatedContext propagatedContext) {
        return context.put(
            ReactorPropagation.PROPAGATED_CONTEXT_REACTOR_CONTEXT_VIEW_KEY,
            propagatedContext
        );
    }

    /**
     * Adds a context element to the Reactor's context.
     * @param context The context to be extended
     * @param contextElement The propagated context element
     * @return The modified context
     */
    @NonNull
    public static Context addContextElement(@NonNull Context context, @NonNull PropagatedContextElement contextElement) {
        return addPropagatedContext(context, findPropagatedContext(context).orElse(PropagatedContext.getOrEmpty()).plus(contextElement));
    }

    /**
     * Finds the context element by the type.
     * @param contextView The Reactor's context
     * @param contextElementType The element type
     * @param <E> The element type
     * @return optional context element
     */
    public static <E extends PropagatedContextElement> Optional<E> findContextElement(@NonNull ContextView contextView,
                                                                                      @NonNull Class<E> contextElementType) {
        return findPropagatedContext(contextView)
            .flatMap(ctx -> ctx.find(contextElementType));
    }

    /**
     * Finds all context elements by the type.
     * @param contextView The Reactor's context
     * @param contextElementType The element type
     * @param <E> All elements if the type
     * @return optional context element
     */
    public static <E extends PropagatedContextElement> Stream<E> findAllContextElements(@NonNull ContextView contextView,
                                                                                        @NonNull Class<E> contextElementType) {
        return findPropagatedContext(contextView)
            .stream()
            .flatMap(ctx -> ctx.findAll(contextElementType));
    }

    /**
     * Finds propagated context in the Reactor's context.
     * @param contextView The context
     * @return optional propagated context
     */
    public static Optional<PropagatedContext> findPropagatedContext(@NonNull ContextView contextView) {
        return contextView.getOrEmpty(ReactorPropagation.PROPAGATED_CONTEXT_REACTOR_CONTEXT_VIEW_KEY)
            .map(ctx -> (PropagatedContext) ctx);
    }

}
