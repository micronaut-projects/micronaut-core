/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.server.netty.converters

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.netty.DefaultHttpClient
import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelOption
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ConverterRegistrySpec extends Specification {

    def "test convert bytebufs"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        CompositeByteBuf compositeByteBuf = Unpooled.compositeBuffer(2)
        compositeByteBuf.addComponent(true, Unpooled.copiedBuffer("test1", StandardCharsets.UTF_8))
        compositeByteBuf.addComponent(true, Unpooled.copiedBuffer("test2", StandardCharsets.UTF_8))


        expect:
        ctx.getBean(ConversionService).convert(compositeByteBuf, String).get() == 'test1test2'
        ctx.getBean(ConversionService).convert(Unpooled.copiedBuffer("test1", StandardCharsets.UTF_8), String).get() == 'test1'


        cleanup:
        compositeByteBuf.release()
        ctx.close()
    }

    def "test convert bytebuf to string after context reset"() {
        given:
        ApplicationContext ctx1 = ApplicationContext.run()
        ApplicationContext ctx2 = ApplicationContext.run()
        ByteBuf buf = Unpooled.wrappedBuffer("foo".bytes)

        expect:
        ctx1.getBean(ConversionService).convert(buf, String).get() == 'foo'
        ctx2.getBean(ConversionService).convert(buf, String).get() == 'foo'

        when:
        ctx2.stop()

        then:
        ctx1.getBean(ConversionService).convert(buf, String).get() == 'foo'

        cleanup:
        buf.release()
        ctx1.close()
    }

    def "test convert string to channel option after context reset"() {
        given:
        ApplicationContext ctx1 = ApplicationContext.run()
        ApplicationContext ctx2 = ApplicationContext.run()

        expect:
        // works as expected
        ctx1.getBean(ConversionService).convert("AUTO_READ", ChannelOption).get() == ChannelOption.AUTO_READ
        ctx2.getBean(ConversionService).convert("AUTO_READ", ChannelOption).get() == ChannelOption.AUTO_READ

        when:
        ctx2.stop()

        then:
        ctx1.getBean(ConversionService).convert("AUTO_READ", ChannelOption).get() == ChannelOption.AUTO_READ

        cleanup:
        ctx1.close()
    }

    def "config properties"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.channel-options.SO_TIMEOUT': 1])

        expect:
        ((DefaultHttpClient) ctx.getBean(HttpClient)).connectionManager().bootstrap.config().options().get(ChannelOption.SO_TIMEOUT) == 1

        cleanup:
        ctx.close()
    }
}
