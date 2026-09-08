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
package io.micronaut.context.exceptions;

import io.micronaut.core.annotation.Internal;

/**
 * Carries an exception thrown by advice applied around a bean's constructor.
 *
 * <p>An exception thrown by advice is the user's exception: when advice around a <em>method</em> throws, the
 * caller sees it as it was thrown. Advice around a <em>constructor</em> runs while the container is creating
 * the bean, where any throwable is otherwise wrapped in a {@link BeanInstantiationException}. This carrier
 * lets the container tell the two apart, so that an exception thrown by construction advice is rethrown
 * unchanged while an exception thrown by the constructor body keeps being wrapped.</p>
 *
 * <p>Instances never reach user code: {@code DefaultBeanContext} unwraps them at the bean creation
 * boundary.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
public final class ConstructorAdviceException extends RuntimeException {

    private final transient Throwable adviceCause;

    /**
     * @param cause The throwable the advice threw
     */
    public ConstructorAdviceException(Throwable cause) {
        super(cause.getMessage(), cause, false, false);
        this.adviceCause = cause;
    }

    /**
     * Rethrows the exception the advice threw.
     *
     * <p>Advice cannot throw a checked exception, so the cause is always a {@link RuntimeException} or an
     * {@link Error}. An {@link Error} is thrown directly; a {@link RuntimeException} is returned so that the
     * call site can {@code throw} it and keep the compiler informed that the flow ends there.</p>
     *
     * @return The runtime exception the advice threw
     */
    public RuntimeException rethrowCause() {
        if (adviceCause instanceof Error error) {
            throw error;
        }
        return (RuntimeException) adviceCause;
    }
}
