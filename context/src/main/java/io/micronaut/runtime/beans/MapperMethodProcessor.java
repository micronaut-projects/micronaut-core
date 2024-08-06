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
import io.micronaut.context.annotation.Mapper;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.function.Supplier;

/**
 * Triggers registering all bean mappers as type converters as well.
 *
 * @author graemerocher
 * @since 4.2.0
 */
@Experimental
@Internal
@Singleton
final class MapperMethodProcessor implements ExecutableMethodProcessor<Mapper> {
    private final MutableConversionService mutableConversionService;
    private final ApplicationContext applicationContext;

    MapperMethodProcessor(MutableConversionService mutableConversionService, ApplicationContext applicationContext) {
        this.mutableConversionService = mutableConversionService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        Class<?>[] argumentTypes = method.getArgumentTypes();
        if (method.hasDeclaredAnnotation(Mapper.class) && argumentTypes.length == 1) {
            var toType = (Class<Object>) method.getReturnType().getType();
            var fromType = (Class<Object>) argumentTypes[0];
            var finalMethod = (ExecutableMethod<Object, Object>) method;
            Supplier<?> beanSupplier = SupplierUtil.memoized(() -> applicationContext.getBean(beanDefinition));
            mutableConversionService.addConverter(
                fromType,
                toType,
                object -> finalMethod.invoke(beanSupplier.get(), object)
            );
        }
    }
}
