/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.util.StringUtils;

/**
 * The media type converters registrar.
 *
 * @author Denis Stepanov
 * @since 3.6.0
 */
@Internal
public final class MediaTypeConvertersRegistrar implements TypeConverterRegistrar {

    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(CharSequence.class, MediaType.class, charSequence -> {
                if (StringUtils.isNotEmpty(charSequence)) {
                    return MediaType.of(charSequence.toString());
                }
                return null;
            }
        );
    }
}
