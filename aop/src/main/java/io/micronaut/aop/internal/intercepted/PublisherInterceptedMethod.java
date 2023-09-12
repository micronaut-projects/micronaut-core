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
package io.micronaut.aop.internal.intercepted;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import org.reactivestreams.Publisher;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * The {@link Publisher} method intercept.
 *
 * @author Denis Stepanov
 * @since 2.1.0
 */
@Internal
@Experimental
sealed class PublisherInterceptedMethod implements InterceptedMethod permits ReactorInterceptedMethod {
    private static final boolean AVAILABLE = ClassUtils.isPresent("io.micronaut.core.async.publisher.Publishers", PublisherInterceptedMethod.class.getClassLoader());
    private final MethodInvocationContext<?, ?> context;
    private final ConversionService conversionService;
    private final Argument<?> returnTypeValue;

    PublisherInterceptedMethod(MethodInvocationContext<?, ?> context, ConversionService conversionService) {
        this.context = context;
        this.conversionService = conversionService;
        this.returnTypeValue = context.getReturnType().asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
    }

    @Override
    public ResultType resultType() {
        return ResultType.PUBLISHER;
    }

    @Override
    public Argument<?> returnTypeValue() {
        return returnTypeValue;
    }

    @Override
    public Publisher<?> interceptResultAsPublisher() {
        return convertToPublisher(context.proceed());
    }

    @Override
    public Publisher<?> interceptResultAsPublisher(Interceptor<?, ?> from) {
        return convertToPublisher(context.proceed(from));
    }

    @Override
    public Publisher<?> interceptResultAsPublisher(ExecutorService executorService) {
        Objects.requireNonNull(executorService);
        final Publisher<?> actual = interceptResultAsPublisher();
        return (Publishers.MicronautPublisher<Object>) s -> executorService.submit(() -> actual.subscribe(s));
    }

    @Override
    public Publisher<?> interceptResult() {
        return interceptResultAsPublisher();
    }

    @Override
    public Publisher<?> interceptResult(Interceptor<?, ?> from) {
        return interceptResultAsPublisher(from);
    }

    @Override
    public Object handleResult(Object result) {
        if (result == null) {
            result = Publishers.empty();
        }
        return convertPublisherResult(context.getReturnType(), result);
    }

    @Override
    public <E extends Throwable> Object handleException(Exception exception) throws E {
        return convertPublisherResult(context.getReturnType(), Publishers.just(exception));
    }

    /**
     * Allows for a soft reference on reactive streams.
     * @param reactiveType The type
     * @return True if the reactive type is convertible to a reactive streams publisher
     */
    static boolean isConvertibleToPublisher(Class<?> reactiveType) {
        return AVAILABLE && Publishers.isConvertibleToPublisher(reactiveType);
    }

    protected Object convertPublisherResult(ReturnType<?> returnType, Object result) {
        if (returnType.getType().isInstance(result)) {
            return result;
        }
        return conversionService.convert(result, returnType.asArgument())
                .orElseThrow(() -> new IllegalStateException("Cannot convert publisher result: " + result + " to '" + returnType.getType().getName() + "'"));
    }

    protected Publisher<?> convertToPublisher(Object result) {
        if (result == null) {
            return Publishers.empty();
        }
        if (result instanceof Publisher publisher) {
            return publisher;
        }
        return conversionService
                .convert(result, Publisher.class)
                .orElseThrow(() -> new IllegalStateException("Cannot convert reactive type " + result + " to 'org.reactivestreams.Publisher'"));
    }
}
