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

import com.mongodb.ReadConcern;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Optional;


/**
 * Converters strings to {@link ReadConcern} objects.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class StringToReadConcernConverter implements TypeConverter<CharSequence, ReadConcern> {
    @Override
    public Optional<ReadConcern> convert(CharSequence object, Class<ReadConcern> targetType, ConversionContext context) {
        String readConcern = object.toString().toUpperCase(Locale.ENGLISH);
        switch (readConcern) {
            case "DEFAULT":
                return Optional.of(ReadConcern.DEFAULT);
            case "LINEARIZABLE":
                return Optional.of(ReadConcern.LINEARIZABLE);
            case "LOCAL":
                return Optional.of(ReadConcern.LOCAL);
            case "MAJORITY":
                return Optional.of(ReadConcern.MAJORITY);
            default:
                return Optional.empty();
        }
    }
}

