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
package io.micronaut.http.netty.channel.converters;

import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.netty.channel.ChannelOption;

/**
 * Creates channel options.
 * @author croudet
 */
@Internal
public interface ChannelOptionFactory {

    /**
     * Creates a channel options.
     * @param name The name of the option.
     * @return A channel option.
     */
    default ChannelOption<?> channelOption(String name) {
        return ChannelOption.valueOf(name);
    }

    /**
     * Converts the specified value given the channel option type.
     * @param option The channel option.
     * @param value The value to convert.
     * @param env The environment use for the conversion.
     * @return The converted value.
     */
    default Object convertValue(ChannelOption<?> option, Object value, Environment env) {
        return DefaultChannelOptionFactory.convertValue(option, ChannelOption.class, value, env);
    }
}
