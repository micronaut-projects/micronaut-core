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
package org.particleframework.function;

import org.particleframework.context.processor.ExecutableMethodProcessor;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.util.StringUtils;
import org.particleframework.http.MediaType;
import org.particleframework.http.codec.MediaTypeCodec;
import org.particleframework.http.codec.MediaTypeCodecRegistry;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.ExecutableMethod;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * <p>Default implementation of the {@link FunctionRegistry} interface</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultFunctionRegistry implements ExecutableMethodProcessor<FunctionBean>, FunctionRegistry, MediaTypeCodecRegistry {
    private final Map<String, ExecutableMethod<?,?>> consumers = new LinkedHashMap<>(1);
    private final Map<String, ExecutableMethod<?,?>> functions = new LinkedHashMap<>(1);
    private final Map<String, ExecutableMethod<?,?>> biFunctions= new LinkedHashMap<>(1);
    private final Map<String, ExecutableMethod<?,?>> suppliers = new LinkedHashMap<>(1);
    private final MediaTypeCodecRegistry decoderRegistry;

    public DefaultFunctionRegistry(MediaTypeCodec...decoders) {
        this.decoderRegistry = MediaTypeCodecRegistry.of(decoders);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<? extends ExecutableMethod<?, ?>> findFirst() {
        return Stream.of(functions, suppliers, consumers, biFunctions)
                .map(all -> {
                    Collection<ExecutableMethod<?, ?>> values = all.values();
                    return values.stream().findFirst();
                }).filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<? extends ExecutableMethod<?, ?>> find(String name) {
        return Stream.of(functions, suppliers, consumers, biFunctions)
                .flatMap(map -> {
                    ExecutableMethod<?, ?> method = map.get(name);
                    if(method == null) {
                        return Stream.empty();
                    }
                    return Stream.of(method);
                })
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<ExecutableMethod<Supplier<T>, T>> findSupplier(String name) {
        ExecutableMethod method = suppliers.get(name);
        if(method != null) {
            return Optional.of(method);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<ExecutableMethod<Consumer<T>, Void>> findConsumer(String name) {
        ExecutableMethod method = consumers.get(name);
        if(method != null) {
            return Optional.of(method);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, R> Optional<ExecutableMethod<java.util.function.Function<T, R>, R>> findFunction(String name) {
        ExecutableMethod method = functions.get(name);
        if(method != null) {
            return Optional.of(method);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, U, R> Optional<ExecutableMethod<BiFunction<T, U, R>, R>> findBiFunction(String name) {
        ExecutableMethod method = biFunctions.get(name);
        if(method != null) {
            return Optional.of(method);
        }
        return Optional.empty();
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        FunctionBean annotation = method.getAnnotation(FunctionBean.class);
        String functionId = annotation.value();
        Class<?> declaringType = method.getDeclaringType();
        if(StringUtils.isEmpty(functionId)) {
            String typeName = declaringType.getSimpleName();
            if(typeName.contains("$")) {
                // generated lambda
                functionId = NameUtils.hyphenate(method.getMethodName());
            }
            else {
                functionId = NameUtils.hyphenate(typeName);
            }
        }

        if(java.util.function.Function.class.isAssignableFrom(declaringType) && method.getMethodName().equals("apply")) {
            registerFunction(method, functionId);
        }
        else if(Consumer.class.isAssignableFrom(declaringType) && method.getMethodName().equals("accept")) {
            registerConsumer(method, functionId);
        }
        else if(BiFunction.class.isAssignableFrom(declaringType) && method.getMethodName().equals("apply")) {
            registerBiFunction(method, functionId);
        }
        else if(Supplier.class.isAssignableFrom(declaringType) && method.getMethodName().equals("get")) {
            registerSupplier(method, functionId);
        }
    }

    private void registerSupplier(ExecutableMethod<?, ?> method, String functionId) {
        suppliers.put(functionId, method);
    }

    private void registerBiFunction(ExecutableMethod<?, ?> method, String functionId) {
        biFunctions.put(functionId, method);
    }

    private void registerConsumer(ExecutableMethod<?, ?> method, String functionId) {
        consumers.put(functionId, method);
    }

    private void registerFunction(ExecutableMethod<?, ?> method, String functionId) {
        functions.put(functionId, method);
    }

    @Override
    public Optional<MediaTypeCodec> findCodec(MediaType mediaType) {
        return decoderRegistry.findCodec(mediaType);
    }
}
