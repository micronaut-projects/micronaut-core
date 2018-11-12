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

package io.micronaut.configuration.elasticsearch.convert;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * @author Puneet Behl
 * @since 1.1.0
 */
@Singleton
@Requires(classes = RestClientBuilder.class)
public class StringToInetAddressConverter implements TypeConverter<CharSequence, InetAddress> {

    private static final Logger LOG = LoggerFactory.getLogger(StringToInetAddressConverter.class);

    @Override
    public Optional<InetAddress> convert(CharSequence object, Class<InetAddress> targetType, ConversionContext context) {
        String address = object.toString();
        try {
            return Optional.of(InetAddress.getByName(address));
        } catch (UnknownHostException e) {
            LOG.error(e.getMessage(), e);
            return Optional.empty();
        }
    }
}
