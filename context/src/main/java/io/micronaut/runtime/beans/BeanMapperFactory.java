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
package io.micronaut.runtime.beans;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMapper;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ArgumentInjectionPoint;

import java.util.Map;

/**
 * Bean mapper factory.
 *
 * @since 4.1.0
 */
@Factory
@Internal
final class BeanMapperFactory {

    /**
     * Inject a bean mapper for the given injection point.
     *
     * @param mapHandler     The map handler
     * @param injectionPoint The injection point
     * @param <I>            The input type
     * @param <O>            The output type
     * @return The mapper
     */
    @Prototype
    <I, O> BeanMapper<I, O> beanMapper(IntrospectionBeanMapHandler mapHandler, ArgumentInjectionPoint<I, O> injectionPoint) {
        Argument<?>[] typeParameters = injectionPoint.asArgument().getTypeParameters();
        if (typeParameters.length == 2) {
            Argument<?> inputType = typeParameters[0];
            Argument<?> outputType = typeParameters[1];
            if (inputType.getType().equals(Map.class)) {
                Argument<?>[] mapParameters = inputType.getTypeParameters();
                if (mapParameters.length == 2 && (!mapParameters[0].isAssignableFrom(String.class) || !mapParameters[1].getType().equals(Object.class))) {
                    throw new BeanInstantiationException("Input map for BeanMapper must have be Map<String, Object>");
                }
                BeanIntrospection<?> introspection = BeanIntrospection.getIntrospection(outputType.getType());
                return new MapBeanMapper<>(mapHandler, introspection);
            } else {
                @SuppressWarnings("unchecked") BeanIntrospection<I> leftIntrospection = (BeanIntrospection<I>) BeanIntrospection.getIntrospection(inputType.getType());
                @SuppressWarnings("unchecked") BeanIntrospection<O> rightIntrospection = (BeanIntrospection<O>) BeanIntrospection.getIntrospection(outputType.getType());
                return new IntrospectionBeanMapper<>(mapHandler, leftIntrospection, rightIntrospection);
            }

        } else {
            throw new BeanInstantiationException("Expected two generic arguments to inject BeanMapper");
        }

    }

    record MapBeanMapper<O, I>(IntrospectionBeanMapHandler mapHandler, BeanIntrospection<?> introspection) implements BeanMapper<I, O> {
        @SuppressWarnings("unchecked")
        @Override
        public O map(I input, Class<O> outputType, MapStrategy mapStrategy) {
            return mapHandler.map(
                    (Map<String, Object>) input,
                    mapStrategy,
                    (BeanIntrospection<O>) introspection
            );
        }

        @SuppressWarnings("unchecked")
        @Override
        public O map(I input, O output, MapStrategy mapStrategy) {
            return mapHandler.map(
                    (Map<String, Object>) input,
                    output,
                    mapStrategy,
                    (BeanIntrospection<O>) introspection
            );
        }
    }

    record IntrospectionBeanMapper<O, I>(
            IntrospectionBeanMapHandler mapHandler,
            BeanIntrospection<I> leftIntrospection,
            BeanIntrospection<O> rightIntrospection) implements BeanMapper<I, O> {

        @Override
        public O map(I input, Class<O> outputType, MapStrategy mapStrategy) {
            return mapHandler.map(
                    input,
                    mapStrategy,
                    leftIntrospection,
                    rightIntrospection
            );
        }

        @Override
        public O map(I input, O output, MapStrategy mapStrategy) {
            return mapHandler.map(
                    input,
                    output,
                    mapStrategy,
                    leftIntrospection,
                    rightIntrospection
            );
        }
    }
}

