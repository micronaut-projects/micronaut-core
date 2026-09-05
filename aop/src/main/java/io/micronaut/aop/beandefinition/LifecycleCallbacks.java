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
package io.micronaut.aop.beandefinition;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.exception.InvocationException;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;

/**
 * Dispatch of the {@code @PostConstruct} and {@code @PreDestroy} callbacks of an intercepted bean through its
 * executable methods definition.
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
final class LifecycleCallbacks {

    private LifecycleCallbacks() {
    }

    /**
     * Invokes a callback on the bean and discards what it returns, since lifecycle interception always yields the
     * bean instance.
     *
     * <p>A private callback is dispatched reflectively, which wraps what it threw. It is unwrapped so that the
     * interceptor chain of the event sees the same exception whether or not the callback needed reflection.</p>
     *
     * @param callback  The callback
     * @param bean      The bean
     * @param arguments The resolved arguments of the callback
     * @param <T>       The bean type
     */
    static <T> void invoke(ExecutableMethod<T, ?> callback, T bean, @Nullable Object[] arguments) {
        try {
            callback.invoke(bean, arguments);
        } catch (InvocationException e) {
            Throwable cause = e.getCause() instanceof InvocationTargetException targetException ? targetException.getCause() : null;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
