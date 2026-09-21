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
package io.micronaut.inject.proxy;

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An internal interface implemented by generated proxy classes.
 * Inject aware version of AOP interface.

 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public interface InterceptedBean {

    /**
     * Returns a defensive copy of the generated executable methods used by the proxy invocation chain.
     *
     * @return The generated executable methods copy
     * @since 5.1.0
     */
    default ExecutableMethod<?, ?>[] interceptedMethods() {
        return new ExecutableMethod[0];
    }

    /**
     * The interceptor registrations the proxy retained when it was constructed.
     *
     * <p>Declared here, in the inject module, so that the context can reach them without depending on the AOP
     * module. A proxy is the only thing that survives from a bean's creation to its destruction, and when the
     * context tracks no registration for the instance the registrations it retained are the only record of the
     * interceptors created for it. {@code io.micronaut.aop.Intercepted} narrows the element type.</p>
     *
     * @return The retained interceptor registrations, never {@code null}
     * @since 5.2.0
     * @deprecated Since 5.3.0 a proxy retains nothing, and this returns an empty list for a proxy generated since
     * 5.3: the beans created for a bean, its non-singleton interceptors among them, are the dependents of its
     * registration. A listener reads them from {@link io.micronaut.context.event.BeanEvent#getDependentBeans()},
     * anything else from {@link BeanRegistration#getDependentBeans()}
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    // The $ prefix marks this as generated-code infrastructure and keeps it clear of any method on the proxied type.
    @SuppressWarnings({"checkstyle:MethodName", "java:S100"})
    default List<? extends BeanRegistration<?>> $interceptorRegistrations() {
        return List.of();
    }

    /**
     * The registration the context created for this proxy, which carries what the bean owns, its non-singleton
     * interceptors among them.
     *
     * <p>A proxy that is the bean, and is held by whoever asked for it, keeps its registration here so that the
     * registration lives exactly as long as the bean: the context finds it again from the instance, to destroy the
     * bean with its dependents, however long ago the bean was created. A reference from the bean to its registration
     * does not keep either alive, as a reference held by the context would whenever a dependent refers back to the
     * bean. A proxy compiled before 5.3 keeps none.</p>
     *
     * @return The registration, or {@code null}
     * @since 5.3.0
     */
    @Internal
    @SuppressWarnings({"checkstyle:MethodName", "java:S100"})
    default @Nullable BeanRegistration<?> $beanRegistration() {
        return null;
    }

    /**
     * Keeps the registration the context created for this proxy, see {@link #$beanRegistration()}.
     *
     * @param registration The registration, or {@code null} once the bean is destroyed
     * @since 5.3.0
     */
    @Internal
    @SuppressWarnings({"checkstyle:MethodName", "java:S100"})
    default void $beanRegistration(@Nullable BeanRegistration<?> registration) {
        // a proxy compiled before 5.3 keeps nothing
    }
}
