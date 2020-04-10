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

import java.lang.reflect.Field;
import java.util.Optional;

import javax.inject.Singleton;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.netty.channel.ChannelOption;

/**
 * Creates channel options.
 * @author croudet
 */
@Internal
@Requires(missingBeans = { EpollChannelOptionFactory.class, KQueueChannelOptionFactory.class })
@Singleton
public class DefaultChannelOptionFactory implements ChannelOptionFactory {

    private static Object processChannelOptionValue(Class<? extends ChannelOption> cls, String name, Object value, Environment env) {
        Optional<Field> declaredField = ReflectionUtils.findField(cls, name);
        if (declaredField.isPresent()) {
            Optional<Class> typeArg = GenericTypeUtils.resolveGenericTypeArgument(declaredField.get());
            if (typeArg.isPresent()) {
                Optional<Object> converted = env.convert(value, typeArg.get());
                value = converted.orElse(value);
            }
        }
        return value;
    }

    /**
     * Process a channel option value.
     * @param option The channel option.
     * @param cls The channel option type.
     * @param value The value to convert.
     * @param env The environment use to convert the value.
     * @return The converted value.
     */
    static Object convertValue(ChannelOption<?> option, Class<? extends ChannelOption> cls, Object value, Environment env) {
        final String name = option.name();
        if (ChannelOption.exists(name)) {
            int idx = name.lastIndexOf('#');
            final String optionName;
            if (idx > 0 && idx < name.length() - 1) {
                // a composed name
                optionName = name.substring(idx);
            } else {
                // A simple name
                optionName = name;
            }
            return processChannelOptionValue(cls, optionName, value, env);
        } else {
            // Unknown option
            return value;
        }
    }

    /**
     * Creates a channel options.
     * @param name The name of the option.
     * @param classes The classes to check.
     * @return A channel option.
     */
    static ChannelOption<?> channelOption(String name, Class<?>... classes) {
        for (Class<?> cls: classes) {
            final String composedName = cls.getName() + '#' + name;
            if (ChannelOption.exists(composedName)) {
                return ChannelOption.valueOf(composedName);
            }
        }
        return ChannelOption.valueOf(name);
    }
}
