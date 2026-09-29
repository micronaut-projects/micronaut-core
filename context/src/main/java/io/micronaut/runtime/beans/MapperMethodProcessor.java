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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Mapper;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.context.watch.BeanWatch;
import io.micronaut.context.watch.ExecutableMethodChange;
import io.micronaut.context.watch.ExecutableMethodWatcher;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registers a converter for every {@link Mapper} method: as a watcher of the methods when the context
 * can be watched, so that a mapper edited in a reload converts through the new bean and one that went
 * converts no more, and as a processor otherwise.
 *
 * @author Denis Stepanov
 * @since 4.1.0
 */
@Experimental
@Internal
@Singleton
final class MapperMethodProcessor implements ExecutableMethodProcessor<Mapper>, ExecutableMethodWatcher<Mapper> {

    private final MutableConversionService mutableConversionService;
    private final ApplicationContext applicationContext;
    @Nullable
    private final BeanWatch watch;
    /**
     * The converter registered for each mapping, which a removal names so that a converter the
     * application registered over it since is left alone.
     */
    private final Map<Mapping, TypeConverter<Object, Object>> converters = new ConcurrentHashMap<>();

    MapperMethodProcessor(MutableConversionService mutableConversionService, ApplicationContext applicationContext) {
        this.mutableConversionService = mutableConversionService;
        this.applicationContext = applicationContext;
        this.watch = applicationContext instanceof WatchableBeanContext watchable
            ? watchable.watchMethods(Mapper.class, this)
            : null;
    }

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        if (watch == null) {
            register(beanDefinition, method);
        }
    }

    @Override
    public void onChange(ExecutableMethodChange<Mapper> change) {
        Set<Mapping> retired = new LinkedHashSet<>();
        for (ExecutableMethodChange.Entry<Mapper> gone : change.removed()) {
            // a replacement is paired by name and parameters: one that maps to another type leaves the retired
            // pair behind unless it is removed, and one that maps the same registers over it
            Optional<Mapping> mapping = mapping(gone.method());
            Optional<Mapping> replacement = change.replacementOf(gone).flatMap(entry -> mapping(entry.method()));
            if (mapping.isPresent() && !mapping.equals(replacement)) {
                // only the converter the mapper registered: one the application registered over it since stays
                TypeConverter<Object, Object> converter = converters.remove(mapping.get());
                if (converter != null && mutableConversionService.removeConverter(mapping.get().from(), mapping.get().to(), converter)) {
                    // a pair left without a converter is one another mapper may map still
                    retired.add(mapping.get());
                }
            }
        }
        for (ExecutableMethodChange.Entry<Mapper> entry : change.added()) {
            // converts through the new generation's bean
            mapping(entry.method()).ifPresent(retired::remove);
            register(entry.definition(), entry.method());
        }
        // a pair another mapper still maps is registered again for that mapper
        for (ExecutableMethodChange.Entry<Mapper> entry : change.current()) {
            if (change.removed().contains(entry)) {
                continue;
            }
            Optional<Mapping> mapping = mapping(entry.method());
            if (mapping.isPresent() && retired.remove(mapping.get())) {
                register(entry.definition(), entry.method());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void register(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        mapping(method).ifPresent(types -> {
            ExecutableMethod<Object, Object> finalMethod = (ExecutableMethod<Object, Object>) method;
            Supplier<?> beanSupplier = SupplierUtil.memoized(() -> applicationContext.getBean(beanDefinition));
            TypeConverter<Object, Object> converter = (object, targetType, context) -> Optional.ofNullable(finalMethod.invoke(beanSupplier.get(), object));
            converters.put(types, converter);
            mutableConversionService.addConverter(types.from(), types.to(), converter);
        });
    }

    @SuppressWarnings("unchecked")
    private static Optional<Mapping> mapping(ExecutableMethod<?, ?> method) {
        Class<?>[] argumentTypes = method.getArgumentTypes();
        if (method.hasDeclaredAnnotation(Mapper.class) && argumentTypes.length == 1) {
            return Optional.of(new Mapping((Class<Object>) argumentTypes[0], (Class<Object>) method.getReturnType().getType()));
        }
        return Optional.empty();
    }

    private record Mapping(Class<Object> from, Class<Object> to) {
    }
}
