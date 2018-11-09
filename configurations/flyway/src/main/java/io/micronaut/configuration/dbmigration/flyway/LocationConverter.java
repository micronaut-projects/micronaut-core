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

package io.micronaut.configuration.dbmigration.flyway;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.Location;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Converts a string to a {@link Location} to configure flyway.
 *
 * @author James Kleeh
 * @since 1.1.0
 */
@Singleton
public class LocationConverter implements TypeConverter<CharSequence, Location> {

    @Override
    public Optional<Location> convert(CharSequence object, Class<Location> targetType, ConversionContext context) {
        try {
            return Optional.of(new Location(object.toString()));
        } catch (FlywayException e) {
            return Optional.empty();
        }
    }
}
