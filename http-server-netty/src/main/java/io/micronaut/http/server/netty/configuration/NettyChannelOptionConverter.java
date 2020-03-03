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
package io.micronaut.http.server.netty.configuration;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.naming.NameUtils;
import io.netty.channel.ChannelOption;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Optional;

/**
 * A {@link TypeConverter} that converts {@link CharSequence} instances to Netty {@link ChannelOption} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Internal
public class NettyChannelOptionConverter implements TypeConverter<CharSequence, ChannelOption> {

    @Override
    public Optional<ChannelOption> convert(CharSequence object, Class<ChannelOption> targetType, ConversionContext context) {
        String str = object.toString();
        String name = NameUtils.underscoreSeparate(str).toUpperCase(Locale.ENGLISH);
        ChannelOption<Object> channelOption = ChannelOption.valueOf(name);
        if (channelOption != null) {
            return Optional.of(channelOption);
        }
        return Optional.empty();
    }
}
