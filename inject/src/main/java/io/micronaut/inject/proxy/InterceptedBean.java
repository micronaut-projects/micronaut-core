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
     */
    // The $ prefix marks this as generated-code infrastructure and keeps it clear of any method on the proxied type.
    @SuppressWarnings({"checkstyle:MethodName", "java:S100"})
    default List<? extends BeanRegistration<?>> $interceptorRegistrations() {
        return List.of();
    }
}
