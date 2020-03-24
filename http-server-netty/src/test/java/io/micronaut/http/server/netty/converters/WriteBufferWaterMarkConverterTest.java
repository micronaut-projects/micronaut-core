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
package io.micronaut.http.server.netty.converters;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author ratcashdev
 */
public class WriteBufferWaterMarkConverterTest {

    NettyHttpServerConfiguration micronautConfig;
    Environment environment;
    long callCount = 0;

    @Before
    public void init() {
        Map<String, Object> params = Map.of(
                "micronaut.server.netty.childOptions.write_buffer_water_mark.high", 262143,
                "micronaut.server.netty.childOptions.write_buffer_water_mark.low", 65535
        );

        ApplicationContext ctx = ApplicationContext.run(params, (String) null);
        micronautConfig = ctx.createBean(NettyHttpServerConfiguration.class);
        environment = ctx.getEnvironment();
    }

    @Test
    public void testNettyConfig() throws IOException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();

        Map<String, Object> configValue
                = (Map<String, Object>) micronautConfig.getChildOptions().get(ChannelOption.WRITE_BUFFER_WATER_MARK);
        assertEquals(configValue.get("high"), 262143);
        assertEquals(configValue.get("low"), 65535);

        processOptions(micronautConfig.getChildOptions(), serverBootstrap::childOption);

        WriteBufferWaterMark val = (WriteBufferWaterMark)
                serverBootstrap.config().childOptions().get(ChannelOption.WRITE_BUFFER_WATER_MARK);
        assertNotNull(val);
        assertEquals(val.high(), 262143);
        assertEquals(val.low(), 65535);
    }

    // method copied from NettyHttpServer
    private void processOptions(Map<ChannelOption, Object> options, BiConsumer<ChannelOption, Object> biConsumer) {
        for (ChannelOption channelOption : options.keySet()) {
            String name = channelOption.name();
            Object value = options.get(channelOption);
            Optional<Field> declaredField = ReflectionUtils.findDeclaredField(ChannelOption.class, name);
            declaredField.ifPresent((field) -> {
                Optional<Class> typeArg = GenericTypeUtils.resolveGenericTypeArgument(field);
                typeArg.ifPresent((arg) -> {
                    Optional converted = environment.convert(value, arg);
                    converted.ifPresent((convertedValue) ->
                        biConsumer.accept(channelOption, convertedValue)
                    );
                });

            });
            if (!declaredField.isPresent()) {
                biConsumer.accept(channelOption, value);
            }
        }
    }
}
