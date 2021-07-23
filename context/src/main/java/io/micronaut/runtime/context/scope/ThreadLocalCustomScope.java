/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.runtime.context.scope;

import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link io.micronaut.context.scope.CustomScope} that stores values in thread local storage.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
final class ThreadLocalCustomScope extends AbstractConcurrentCustomScope<ThreadLocal> {

    private final java.lang.ThreadLocal<Map<BeanIdentifier, CreatedBean<?>>> threadScope = java.lang.ThreadLocal.withInitial(HashMap::new);

    /**
     * Default constructor.
     */
    protected ThreadLocalCustomScope() {
        super(ThreadLocal.class);
    }

    @NonNull
    @Override
    protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
        return threadScope.get();
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public ThreadLocalCustomScope start() {
        return this;
    }

    @Override
    public void close() {
        threadScope.remove();
    }
}
