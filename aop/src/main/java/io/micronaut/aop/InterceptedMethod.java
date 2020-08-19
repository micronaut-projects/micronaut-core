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
package io.micronaut.aop;

import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletionStage;

/**
 * The intercept method supporting intercepting different reactive invocations.
 *
 * @author Denis Stepanov
 * @since 2.1.0
 */
@Experimental
public interface InterceptedMethod {

    /**
     * Creates a new instance of intercept method supporting intercepting different reactive invocations.
     *
     * @param context The {@link MethodInvocationContext}
     * @return The {@link InterceptedMethod}
     */
    static InterceptedMethod of(MethodInvocationContext<?, ?> context) {
        return InterceptedMethodUtil.of(context);
    }

    /**
     * Returns result type of the method.
     *
     * @return The {@link ResultType}
     */
    ResultType resultType();

    /**
     * Returns result type value.
     *
     * @return The return type value.
     */
    Argument<?> returnTypeValue();

    /**
     * Proceeds with invocation of {@link InvocationContext#proceed()} and converts result to appropriate type.
     *
     * @return The intercepted result
     */
    Object interceptResult();


    /**
     * Proceeds with invocation of {@link InvocationContext#proceed(Interceptor)} and converts result to appropriate type.
     *
     * @param from The interceptor to start from
     * @return The intercepted result
     */
    Object interceptResult(Interceptor<?, ?> from);

    /**
     * Proceeds with invocation of {@link InvocationContext#proceed()} and converts result to {@link CompletionStage}.
     *
     * @return The intercepted result
     */
    default CompletionStage<?> interceptResultAsCompletionStage() {
        if (resultType() != ResultType.COMPLETION_STAGE) {
            throw new ConfigurationException("Cannot return `CompletionStage` result from '" + resultType() + "' interceptor");
        }
        return (CompletionStage<?>) interceptResult();
    }

    /**
     * Proceeds with invocation of {@link InvocationContext#proceed()} and converts result to {@link Publisher}.
     *
     * @return The intercepted result
     */
    default Publisher<?> interceptResultAsPublisher() {
        if (resultType() != ResultType.PUBLISHER) {
            throw new ConfigurationException("Cannot return `Publisher` result from '" + resultType() + "' interceptor");
        }
        return (Publisher<?>) interceptResult();
    }

    /**
     * Proceeds with invocation of {@link InvocationContext#proceed(Interceptor)} and converts result to {@link CompletionStage}.
     *
     * @param from The interceptor to start from
     * @return The intercepted result
     */
    default CompletionStage<?> interceptResultAsCompletionStage(Interceptor<?, ?> from) {
        if (resultType() != ResultType.COMPLETION_STAGE) {
            throw new ConfigurationException("Cannot return `CompletionStage` result from '" + resultType() + "' interceptor");
        }
        return (CompletionStage<?>) interceptResult(from);
    }

    /**
     * Proceeds with invocation of {@link InvocationContext#proceed(Interceptor)} and converts result to {@link Publisher}.
     *
     * @param from The interceptor to start from
     * @return The intercepted result
     */
    default Publisher<?> interceptResultAsPublisher(Interceptor<?, ?> from) {
        if (resultType() != ResultType.PUBLISHER) {
            throw new ConfigurationException("Cannot return `Publisher` result from '" + resultType() + "' interceptor");
        }
        return (Publisher<?>) interceptResult(from);
    }


    /**
     * Handle the value that should be the result of the invocation.
     *
     * @param result The result of the invocation
     * @return The result of the invocation being returned from the interceptor
     */
    Object handleResult(Object result);

    /**
     * Handle the exception that should be thrown out of the invocation.
     *
     * @param exception The exception
     * @param <E> Sneaky throws helper
     * @return The result of the invocation being returned from the interceptor
     * @throws E The exception
     */
    <E extends Throwable> Object handleException(Exception exception) throws E;

    /**
     * Indicated unsupported return type.
     *
     * @return The result of the invocation being returned from the interceptor
     */
    default Object unsupported() {
        throw new ConfigurationException("Cannot intercept method invocation, missing '" + resultType() + "' interceptor configured");
    }

    /**
     * Possible result types.
     */
    enum ResultType {
        COMPLETION_STAGE, PUBLISHER, SYNCHRONOUS
    }

}
