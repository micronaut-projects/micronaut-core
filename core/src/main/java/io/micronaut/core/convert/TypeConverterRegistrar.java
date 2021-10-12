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
package io.micronaut.core.convert;

import io.micronaut.core.annotation.Indexed;

/**
 * An interface for classes that register type conversions with the {@link ConversionService}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Indexed(TypeConverterRegistrar.class)
public interface TypeConverterRegistrar {

    /**
     * Interface for registrars of {@link TypeConverter} instances.
     *
     * @param conversionService The {@link ConversionService}
     */
    void register(ConversionService<?> conversionService);
}
