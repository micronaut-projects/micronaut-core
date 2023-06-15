/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.ReturnType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The Reactor method intercept.
 *
 * @author Denis Stepanov
 * @since 2.1.0
 */
@Internal
@Experimental
final class ReactorInterceptedMethod extends PublisherInterceptedMethod {
    public static final boolean REACTOR_AVAILABLE = ClassUtils.isPresent("reactor.core.publisher.Mono", ReactorInterceptedMethod.class.getClassLoader());

    ReactorInterceptedMethod(MethodInvocationContext<?, ?> context, ConversionService conversionService) {
        super(context, conversionService);
    }

    @Override
    protected Object convertPublisherResult(ReturnType<?> returnType, Object result) {
        return captureContext(super.convertPublisherResult(returnType, result));
    }

    @Override
    protected Publisher<?> convertToPublisher(Object result) {
        return captureContext(super.convertToPublisher(result));
    }

    private <T> T captureContext(T result) {
        if (!PropagatedContext.exists()) {
            return result;
        }
        if (result instanceof Mono<?> mono) {
            PropagatedContext propagatedContext = PropagatedContext.get();
            return (T) mono.contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, propagatedContext));
        }
        if (result instanceof Flux<?> flux) {
            PropagatedContext propagatedContext = PropagatedContext.get();
            return (T) flux.contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, propagatedContext));
        }
        return result;
    }
}
