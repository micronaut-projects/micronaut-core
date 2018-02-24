/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.function.web;

import org.particleframework.context.ExecutionHandleLocator;
import org.particleframework.context.annotation.Replaces;
import org.particleframework.context.processor.ExecutableMethodProcessor;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.core.util.StringUtils;
import org.particleframework.function.DefaultLocalFunctionRegistry;
import org.particleframework.function.FunctionBean;
import org.particleframework.function.LocalFunctionRegistry;
import org.particleframework.http.MediaType;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.web.router.DefaultRouteBuilder;
import org.particleframework.web.router.UriRoute;

import javax.inject.Singleton;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Process methods for {@link FunctionBean} instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Replaces(DefaultLocalFunctionRegistry.class)
public class AnnotatedFunctionRouteBuilder extends DefaultRouteBuilder implements ExecutableMethodProcessor<FunctionBean>, LocalFunctionRegistry {


    private final LocalFunctionRegistry localFunctionRegistry;

    public AnnotatedFunctionRouteBuilder(
            ExecutionHandleLocator executionHandleLocator,
            UriNamingStrategy uriNamingStrategy,
            ConversionService<?> conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        this.localFunctionRegistry = new DefaultLocalFunctionRegistry();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        FunctionBean annotation = method.getAnnotation(FunctionBean.class);
        if(annotation != null) {
            String functionPath = annotation.value();
            Class<?> declaringType = method.getDeclaringType();
            if(StringUtils.isEmpty(functionPath)) {
                String typeName = declaringType.getSimpleName();
                if(typeName.contains("$")) {
                    // generated lambda
                    functionPath = "/" + NameUtils.hyphenate(method.getMethodName());
                }
                else {
                    functionPath = "/" + NameUtils.hyphenate(typeName);
                }
            }
            else {
                functionPath = "/" + functionPath;
            }

            UriRoute route = null;
            if(Stream.of(java.util.function.Function.class, Consumer.class, BiFunction.class, BiConsumer.class).anyMatch(type -> type.isAssignableFrom(declaringType))) {
                route = POST(functionPath, method);

            }
            else if(Supplier.class.isAssignableFrom(declaringType)) {
                route = GET(functionPath, method);
            }

            if(route != null) {
                Class[] argumentTypes = method.getArgumentTypes();
                int argCount = argumentTypes.length;
                if(argCount > 0) {
                    if(argCount == 2 || !ClassUtils.isJavaLangType(argumentTypes[0])) {
                        route.consumes(MediaType.APPLICATION_JSON_TYPE);
                    }
                    else {
                        route.body(method.getArgumentNames()[0])
                                .acceptAll();
                    }
                }
                ((ExecutableMethodProcessor) localFunctionRegistry).process(beanDefinition, method);
            }
        }
    }


    @Override
    public <T, R> Optional<? extends ExecutableMethod<T, R>> findFirst() {
        return localFunctionRegistry.findFirst();
    }

    @Override
    public <T, R> Optional<? extends ExecutableMethod<T, R>> find(String name) {
        return localFunctionRegistry.find(name);
    }

    @Override
    public <T> Optional<ExecutableMethod<Supplier<T>, T>> findSupplier(String name) {
        return localFunctionRegistry.findSupplier(name);
    }

    @Override
    public <T> Optional<ExecutableMethod<Consumer<T>, Void>> findConsumer(String name) {
        return localFunctionRegistry.findConsumer(name);
    }

    @Override
    public <T, R> Optional<ExecutableMethod<java.util.function.Function<T, R>, R>> findFunction(String name) {
        return localFunctionRegistry.findFunction(name);
    }

    @Override
    public <T, U, R> Optional<ExecutableMethod<BiFunction<T, U, R>, R>> findBiFunction(String name) {
        return localFunctionRegistry.findBiFunction(name);
    }
}
