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

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import java.util.List;
import io.micronaut.inject.ExecutableMethod;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

/**
 * Executable method that delegates {@link DisposableIntercepted} disposal to the interceptor chain.
 *
 * @param <T> The intercepted bean type
 * @author Denis Stepanov
 * @since 5.1.0
 */
@Internal
@SuppressWarnings("unchecked")
final class InterceptedDisposeMethod<T> extends InterceptedMethod<T, T> {

    private final DisposableIntercepted<T> disposableIntercepted;
    private final BeanResolutionContext beanResolutionContext;
    private final BeanContext beanContext;
    private final T bean;

    /**
     * @param disposableIntercepted The intercepted dispose bean definition
     * @param beanResolutionContext            The resolution context
     * @param beanContext                      The bean context
     * @param bean                             The bean being disposed
     */
    InterceptedDisposeMethod(DisposableIntercepted<T> disposableIntercepted,
                             BeanResolutionContext beanResolutionContext,
                             BeanContext beanContext,
                             T bean) {
        super(describedType(disposableIntercepted), describedName(disposableIntercepted), Argument.of(disposableIntercepted.getBeanType()));
        this.disposableIntercepted = disposableIntercepted;
        this.beanResolutionContext = beanResolutionContext;
        this.beanContext = beanContext;
        this.bean = bean;
    }

    /**
     * The callback of the bean, resolved by the executable method that stands for it rather than by looking a
     * name up on the declaring type: the chain declares no arguments, since a callback's arguments are resolved
     * only once it proceeds, so a name and an empty signature would not find a callback that takes any.
     */
    @Override
    public Method getTargetMethod() {
        ExecutableMethod<T, ?> callback = described(disposableIntercepted);
        return callback == null ? super.getTargetMethod() : callback.getTargetMethod();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return disposableIntercepted.getAnnotationMetadata();
    }

    @Override
    protected T invokeInternal(T instance, @Nullable Object[] arguments) {
        return disposableIntercepted.doDispose(beanResolutionContext, beanContext, bean);
    }

    /**
     * The callback of the event the chain describes itself as, which is the last one it invokes: the most derived,
     * and so the one declared by the bean itself where it declares any. A chain stands for the whole event, but an
     * interceptor asking what it is intercepting is better answered with a method the bean actually declares than
     * with the synthetic {@code dispose} of the definition.
     *
     * @param definition The definition
     * @param <T> The bean type
     * @return The callback, or {@code null} when the bean declares none
     */
    @Nullable
    private static <T> ExecutableMethod<T, ?> described(DisposableIntercepted<T> definition) {
        List<ExecutableMethod<T, ?>> callbacks = definition.getPreDestroyExecutableMethods();
        return callbacks.isEmpty() ? null : callbacks.get(callbacks.size() - 1);
    }

    private static <T> Class<T> describedType(DisposableIntercepted<T> definition) {
        ExecutableMethod<T, ?> callback = described(definition);
        return callback == null ? definition.getBeanType() : (Class<T>) callback.getDeclaringType();
    }

    private static <T> String describedName(DisposableIntercepted<T> definition) {
        ExecutableMethod<T, ?> callback = described(definition);
        return callback == null ? "dispose" : callback.getMethodName();
    }
}
