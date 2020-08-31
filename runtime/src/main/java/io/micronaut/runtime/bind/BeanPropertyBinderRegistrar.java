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
package io.micronaut.runtime.bind;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link BeanPropertyBinder} type convert registrar.
 *
 * @author Denis Stepanov
 * @since 2.1.0
 */
@Singleton
@Requires(beans = BeanPropertyBinder.class)
public class BeanPropertyBinderRegistrar implements TypeConverterRegistrar {

    private static final Logger LOG = LoggerFactory.getLogger(BeanPropertyBinderRegistrar.class);

    private final BeanPropertyBinder beanPropertyBinder;

    public BeanPropertyBinderRegistrar(BeanPropertyBinder beanPropertyBinder) {
        this.beanPropertyBinder = beanPropertyBinder;
    }

    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(Map.class, Object.class, (map, targetType, context) -> {
            ArgumentConversionContext<Object> conversionContext;
            if (context instanceof ArgumentConversionContext) {
                conversionContext = (ArgumentConversionContext<Object>) context;
            } else {
                conversionContext = ConversionContext.of(targetType);
            }
            ArgumentBinder binder = beanPropertyBinder;
            ArgumentBinder.BindingResult<Object> result = binder.bind(conversionContext, map);
            for (ConversionError conversionError : result.getConversionErrors()) {
                Exception cause = conversionError.getCause();
                LOG.error("Bean property bind to '{}' error '{}'", targetType, cause.getMessage());
            }
            return result.getValue();
        });
        conversionService.addConverter(Map.class, List.class, (map, targetType, context) -> Optional.of(Collections.singletonList(map)));
    }
}
