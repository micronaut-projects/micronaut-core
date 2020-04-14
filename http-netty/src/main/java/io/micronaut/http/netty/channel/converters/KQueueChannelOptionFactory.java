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
package io.micronaut.http.netty.channel.converters;

import java.util.Map;
import java.util.Optional;

import javax.inject.Singleton;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.http.netty.channel.KQueueAvailabilityCondition;
import io.netty.channel.ChannelOption;
import io.netty.channel.kqueue.AcceptFilter;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueChannelOption;
import io.netty.channel.unix.UnixChannelOption;

/**
 * Creates channel options.
 * @author croudet
 */
@Internal
@Singleton
@Requires(classes = KQueue.class, condition = KQueueAvailabilityCondition.class)
@TypeHint(value = KQueueChannelOption.class, accessType = TypeHint.AccessType.ALL_DECLARED_FIELDS)
public class KQueueChannelOptionFactory implements ChannelOptionFactory, TypeConverterRegistrar {

    static {
        // force loading the class for the channelOption to work
        KQueueChannelOption.SO_ACCEPTFILTER.name();
    }

    @Override
    public ChannelOption<?> channelOption(String name) {
        return DefaultChannelOptionFactory.channelOption(name, KQueueChannelOption.class, UnixChannelOption.class);
    }

    @Override
    public Object convertValue(ChannelOption<?> option, Object value, Environment env) {
        return DefaultChannelOptionFactory.convertValue(option, KQueueChannelOption.class, value, env);
    }

    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(
                Map.class,
                AcceptFilter.class,
                (map, targetType, context) -> {
                    Object filterName = map.get("filterName");
                    Object filterArgs = map.get("filterArgs");
                    if (filterName != null && filterArgs != null) {
                        return Optional.of(new AcceptFilter(filterName.toString(), filterArgs.toString()));
                    }
                    return Optional.empty();
                }
        );
    }
}
