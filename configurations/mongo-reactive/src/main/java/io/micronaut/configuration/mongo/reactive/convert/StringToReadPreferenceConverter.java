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

import com.mongodb.ReadPreference;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Optional;

/**
 * Converters strings to {@link com.mongodb.ReadPreference} objects.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class StringToReadPreferenceConverter implements TypeConverter<CharSequence, ReadPreference> {
    @Override
    public Optional<ReadPreference> convert(CharSequence object, Class<ReadPreference> targetType, ConversionContext context) {
        String readConcern = object.toString().toUpperCase(Locale.ENGLISH);
        switch (readConcern) {
            case "PRIMARY":
                return Optional.of(ReadPreference.primary());
            case "PRIMARY_PREFERRED":
                return Optional.of(ReadPreference.primaryPreferred());
            case "SECONDARY":
                return Optional.of(ReadPreference.secondary());
            case "SECONDARY_PREFERRED":
                return Optional.of(ReadPreference.secondaryPreferred());
            case "NEAREST":
                return Optional.of(ReadPreference.nearest());
            default:
                return Optional.empty();
        }
    }
}
