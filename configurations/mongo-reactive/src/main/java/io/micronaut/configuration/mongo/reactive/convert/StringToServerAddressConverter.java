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

import com.mongodb.ServerAddress;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class StringToServerAddressConverter implements TypeConverter<CharSequence, ServerAddress> {
    @Override
    public Optional<ServerAddress> convert(CharSequence object, Class<ServerAddress> targetType, ConversionContext context) {
        String address = object.toString();
        if (address.contains(":")) {
            String[] hostAndPort = address.split(":");
            try {
                return Optional.of(new ServerAddress(hostAndPort[0], Integer.valueOf(hostAndPort[1])));
            } catch (NumberFormatException e) {
                context.reject(address, e);
                return Optional.empty();
            }
        } else {
            return Optional.of(new ServerAddress(address));
        }
    }
}
