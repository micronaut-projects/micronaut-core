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
 * <p>Used for flow control only: it is thrown and caught between
 * {@code ConstructorInterceptorChain#instantiate} and the bean creation boundary in
 * {@code DefaultBeanContext}, and never reaches user code. It carries no stack trace of its own — the
 * exception the advice threw keeps its own, and it is that one the caller sees.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
public final class ConstructorAdviceException extends RuntimeException {

    private final transient RuntimeException adviceCause;

    /**
     * @param cause The exception the advice threw
     */
    public ConstructorAdviceException(RuntimeException cause) {
        super(cause.getMessage(), cause);
        this.adviceCause = cause;
    }

    /**
     * @return The exception the advice threw, to be rethrown in place of this carrier
     */
    public RuntimeException getAdviceCause() {
        return adviceCause;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
