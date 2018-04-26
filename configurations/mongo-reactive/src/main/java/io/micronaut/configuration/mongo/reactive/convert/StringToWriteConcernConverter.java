/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.mongo.reactive.convert;

import com.mongodb.WriteConcern;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Optional;

/**
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class StringToWriteConcernConverter implements TypeConverter<CharSequence, WriteConcern> {
    @Override
    public Optional<WriteConcern> convert(CharSequence object, Class<WriteConcern> targetType, ConversionContext context) {
        return Optional.ofNullable(WriteConcern.valueOf(object.toString().toUpperCase(Locale.ENGLISH)));
    }
}

