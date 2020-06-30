/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.function.web;

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.metadata.ServiceInstanceMetadataContributor;
import io.micronaut.function.DefaultLocalFunctionRegistry;
import io.micronaut.function.FunctionBean;
import io.micronaut.function.LocalFunctionRegistry;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.web.router.DefaultRouteBuilder;
import io.micronaut.web.router.UriRoute;

import javax.inject.Singleton;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Process methods for {@link FunctionBean} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Replaces(DefaultLocalFunctionRegistry.class)
public class AnnotatedFunctionRouteBuilder
    extends DefaultRouteBuilder
    implements ExecutableMethodProcessor<FunctionBean>, LocalFunctionRegistry, ServiceInstanceMetadataContributor, MediaTypeCodecRegistry {

    private final LocalFunctionRegistry localFunctionRegistry;
    private final String contextPath;
    private final Map<String, URI> availableFunctions = new ConcurrentHashMap<>();

    /**
     * Constructor.
     * @param executionHandleLocator executionHandleLocator
     * @param uriNamingStrategy uriNamingStrategy
     * @param conversionService conversionService
     * @param codecRegistry codecRegistry
     * @param contextPath contextPath
     */
    public AnnotatedFunctionRouteBuilder(
        ExecutionHandleLocator executionHandleLocator,
        UriNamingStrategy uriNamingStrategy,
        ConversionService<?> conversionService,
        MediaTypeCodecRegistry codecRegistry,
        @Value("${micronaut.function.context-path:/}") String contextPath) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        this.localFunctionRegistry = new DefaultLocalFunctionRegistry(codecRegistry);
        this.contextPath = contextPath.endsWith("/") ? contextPath : contextPath + '/';
    }

    @SuppressWarnings("unchecked")
    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        if (beanDefinition.hasAnnotation(FunctionBean.class)) {
            String methodName = method.getMethodName();
            Class<?> declaringType = method.getDeclaringType();
            String functionName = beanDefinition.stringValue(FunctionBean.class).orElse(methodName);
            String functionMethod = beanDefinition.stringValue(FunctionBean.class, "method").orElse(null);

            List<UriRoute> routes = new ArrayList<>(2);
            MediaType[] consumes = Arrays.stream(method.stringValues(Consumes.class)).map(MediaType::new).toArray(MediaType[]::new);
            MediaType[] produces = Arrays.stream(method.stringValues(Produces.class)).map(MediaType::new).toArray(MediaType[]::new);

            if (Stream.of(java.util.function.Function.class, Consumer.class, BiFunction.class, BiConsumer.class).anyMatch(type -> type.isAssignableFrom(declaringType))) {
                if (methodName.equals("accept") || methodName.equals("apply") || methodName.equals(functionMethod)) {
                    String functionPath = resolveFunctionPath(methodName, declaringType, functionName);
                    String[] argumentNames = method.getArgumentNames();
                    String argumentName = argumentNames[0];
                    int argCount = argumentNames.length;

                    UriRoute route = POST(functionPath, beanDefinition, method);
                    routes.add(route);
                    if (argCount == 1) {
                        route.body(argumentName);
                    }

                    List<Argument<?>> typeArguments = beanDefinition.getTypeArguments();
                    if (!typeArguments.isEmpty()) {
                        int size = typeArguments.size();

                        Argument<?> firstArgument = typeArguments.get(0);
                        if (size < 3 && ClassUtils.isJavaLangType(firstArgument.getType()) && consumes == null) {
                            consumes = new MediaType[] {MediaType.TEXT_PLAIN_TYPE, MediaType.APPLICATION_JSON_TYPE};
                        }

                        if (size < 3) {
                            route.body(Argument.of(firstArgument.getType(), argumentName));
                        }

                        if (size > 1) {
                            Argument<?> argument = typeArguments.get(size == 3 ? 2 : 1);
                            if (ClassUtils.isJavaLangType(argument.getType()) && produces == null) {
                                produces = new MediaType[] {MediaType.TEXT_PLAIN_TYPE, MediaType.APPLICATION_JSON_TYPE};
                            }
                        }
                    } else {
                        if (argCount == 1 && ClassUtils.isJavaLangType(method.getArgumentTypes()[0]) && consumes == null) {
                            consumes = new MediaType[] {MediaType.TEXT_PLAIN_TYPE, MediaType.APPLICATION_JSON_TYPE};
                        }
                    }
                }
            } else if (Supplier.class.isAssignableFrom(declaringType) && methodName.equals("get")) {
                String functionPath = resolveFunctionPath(methodName, declaringType, functionName);
                routes.add(GET(functionPath, beanDefinition, method));
                routes.add(HEAD(functionPath, beanDefinition, method));
            } else {
                if (StringUtils.isNotEmpty(functionMethod) && functionMethod.equals(methodName)) {
                    Argument[] argumentTypes = method.getArguments();
                    int argCount = argumentTypes.length;
                    if (argCount < 3) {
                        String functionPath = resolveFunctionPath(methodName, declaringType, functionName);
                        if (argCount == 0) {
                            routes.add(GET(functionPath, beanDefinition, method));
                            routes.add(HEAD(functionPath, beanDefinition, method));
                        } else {
                            UriRoute route = POST(functionPath, beanDefinition, method);
                            routes.add(route);
                            if (argCount == 2 || !ClassUtils.isJavaLangType(argumentTypes[0].getType())) {
                                if (consumes == null) {
                                    consumes = new MediaType[] {MediaType.APPLICATION_JSON_TYPE};
                                }
                            } else {
                                route.body(method.getArgumentNames()[0])
                                        .consumesAll();
                            }
                        }
                    }
                }
            }

            if (!routes.isEmpty()) {
                for (UriRoute route: routes) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Created Route to Function: {}", route);
                    }

                    if (consumes != null) {
                        route.consumes(consumes);
                    }

                    if (produces != null) {
                        route.produces(produces);
                    }
                }

                String functionPath = resolveFunctionPath(methodName, declaringType, functionName);
                availableFunctions.put(functionName, URI.create(functionPath));

                ((ExecutableMethodProcessor) localFunctionRegistry).process(beanDefinition, method);
            }
        }
    }

    private String resolveFunctionPath(String methodName, Class<?> declaringType, String functionName) {
        String functionPath = functionName;
        if (StringUtils.isEmpty(functionPath)) {
            String typeName = declaringType.getSimpleName();
            if (typeName.contains("$")) {
                // generated lambda
                functionPath = contextPath + NameUtils.hyphenate(methodName);
            } else {
                functionPath = contextPath + NameUtils.hyphenate(typeName);
            }
        } else {
            functionPath = contextPath + functionPath;
        }
        return functionPath;
    }

    /**
     * A map of available functions with the key being the function name and the value being the function URI.
     *
     * @return A map of functions
     */
    @Override
    public Map<String, URI> getAvailableFunctions() {
        return Collections.unmodifiableMap(availableFunctions);
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

    @Override
    public void contribute(ServiceInstance instance, Map<String, String> metadata) {
        for (Map.Entry<String, URI> entry : availableFunctions.entrySet()) {
            String functionName = entry.getKey();
            metadata.put(FUNCTION_PREFIX + functionName, entry.getValue().toString());
        }
    }

    @Override
    public Optional<MediaTypeCodec> findCodec(MediaType mediaType) {
        if (localFunctionRegistry instanceof MediaTypeCodecRegistry) {
            return ((MediaTypeCodecRegistry) localFunctionRegistry).findCodec(mediaType);
        }
        return Optional.empty();
    }

    @Override
    public Optional<MediaTypeCodec> findCodec(MediaType mediaType, Class<?> type) {
        if (localFunctionRegistry instanceof MediaTypeCodecRegistry) {
            return ((MediaTypeCodecRegistry) localFunctionRegistry).findCodec(mediaType, type);
        }
        return Optional.empty();
    }

    @Override
    public Collection<MediaTypeCodec> getCodecs() {
        if (localFunctionRegistry instanceof MediaTypeCodecRegistry) {
            return ((MediaTypeCodecRegistry) localFunctionRegistry).getCodecs();
        }
        return Collections.emptyList();
    }
}
