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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Executable method that stands for one {@code @PostConstruct} or {@code @PreDestroy} callback of a bean in the
 * interceptor chain of that callback.
 *
 * <p>It describes the callback: its declaring type, name, arguments, annotation metadata and target method are
 * those of the callback, so interceptor bindings resolve from the bean class through the metadata hierarchy of the
 * callback and an interceptor can inspect the method it is about to run. Invoking it dispatches the callback
 * through the executable methods definition of the bean, without reflection, and returns the bean, which is the
 * contract of lifecycle interception: {@code proceed()} always yields the bean instance.</p>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
final class LifecycleCallbackMethod<T> extends InterceptedMethod<T, T> {

    private final ExecutableMethod<T, ?> callback;

    /**
     * @param definition The bean definition
     * @param callback   The callback
     */
    LifecycleCallbackMethod(BeanDefinition<T> definition, ExecutableMethod<T, ?> callback) {
        super(callback.getDeclaringType(), callback.getMethodName(), Argument.of(definition.getBeanType()), callback.getArguments());
        this.callback = callback;
    }

    @Override
    protected AnnotationMetadata resolveAnnotationMetadata() {
        return callback.getAnnotationMetadata();
    }

    @Override
    public boolean isSuspend() {
        return callback.isSuspend();
    }

    @Override
    public boolean isAbstract() {
        return callback.isAbstract();
    }

    @Override
    public Method getTargetMethod() {
        return callback.getTargetMethod();
    }

    @Override
    public List<ExecutableMethod<T, ?>> getExecutableMethods() {
        return List.of(this);
    }

    @Override
    protected T invokeInternal(T instance, @Nullable Object[] arguments) {
        callback.invoke(instance, arguments);
        return instance;
    }

    @Override
    public String toString() {
        return callback.toString();
    }
}
