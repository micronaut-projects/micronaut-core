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

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;

/**
 * Questions about the scope of a definition that more than one part of the context asks.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class BeanScopes {

    private BeanScopes() {
    }

    /**
     * Whether a bean of the given definition is created for no scope, and so held by whoever asked for it: a
     * prototype, or a bean with no scope at all.
     *
     * @param definition The definition
     * @return {@code true} for a prototype or a bean with no scope
     */
    static boolean isUnscoped(BeanDefinition<?> definition) {
        if (definition.isSingleton()) {
            return false;
        }
        String scope = definition.getScopeName().orElse(null);
        return scope == null || Prototype.class.getName().equals(scope);
    }

    /**
     * Whether a bean of the given definition belongs to a scope of its own, which decides how long it lives and
     * when it is replaced: neither a singleton nor a bean held by whoever asked for it.
     *
     * @param definition The definition
     * @return {@code true} for a bean of a custom scope
     */
    static boolean isCustomScoped(BeanDefinition<?> definition) {
        return !definition.isSingleton() && !isUnscoped(definition);
    }
}
