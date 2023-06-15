/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.context.propagation;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;

import java.util.ArrayDeque;

/**
 *  Integrate {@link PropagatedContext} with Reactor and Micrometer Context Propagation.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Requires(classes = {Flux.class, ContextRegistry.class, PropagatedContext.class})
@Context
@Internal
final class ReactorInstrumentation {

    private static final PropagatedContextThreadLocalAccessor ACCESSOR = new PropagatedContextThreadLocalAccessor();

    @PostConstruct
    void init() {
        Hooks.enableAutomaticContextPropagation();
        ContextRegistry.getInstance()
            .registerThreadLocalAccessor(ACCESSOR);
    }

    @PreDestroy
    void removeInstrumentation() {
        ContextRegistry.getInstance().removeThreadLocalAccessor(ACCESSOR.key());
    }

    private static class PropagatedContextThreadLocalAccessor implements ThreadLocalAccessor<PropagatedContext> {

        private final ThreadLocal<ArrayDeque<PropagatedContext.Scope>> localScopes = new ThreadLocal<>();

        @Override
        public String key() {
            return ReactorPropagation.PROPAGATED_CONTEXT_REACTOR_CONTEXT_VIEW_KEY;
        }

        @Override
        public PropagatedContext getValue() {
            return PropagatedContext.find().orElse(null);
        }

        @Override
        public void setValue(PropagatedContext propagatedContext) {
            ArrayDeque<PropagatedContext.Scope> scopes = localScopes.get();
            if (scopes == null) {
                scopes = new ArrayDeque<>(5);
                localScopes.set(scopes);
            }
            scopes.push(propagatedContext.propagate());
        }

        @Override
        public void setValue() {
            // Do nothing
        }

        @Override
        public void restore(PropagatedContext previousValue) {
            ArrayDeque<PropagatedContext.Scope> scopes = localScopes.get();
            if (scopes != null && !scopes.isEmpty()) {
                scopes.pop().close();
                if (scopes.isEmpty()) {
                    localScopes.remove();
                }
            }
        }

        @Override
        public void restore() {
        }
    }
}
