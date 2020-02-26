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
package io.micronaut.http.client.converters;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.http.client.LoadBalancer;

import javax.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;
import java.util.function.Function;

/**
 * Converters from URL to {@link LoadBalancer} interface.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class LoadBalancerConverters implements TypeConverterRegistrar {
    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(URI.class, LoadBalancer.class, (TypeConverter<URI, LoadBalancer>) (object, targetType, context) -> {
            try {
                return Optional.of(LoadBalancer.fixed(object.toURL()));
            } catch (MalformedURLException e) {
                context.reject(e);
                return Optional.empty();
            }
        });
        conversionService.addConverter(URL.class, LoadBalancer.class, (Function<URL, LoadBalancer>) LoadBalancer::fixed);
        conversionService.addConverter(String.class, LoadBalancer.class, (Function<String, LoadBalancer>) url -> {
            try {
                return LoadBalancer.fixed(new URL(url));
            } catch (MalformedURLException e) {
                return null;
            }
        });
    }
}
