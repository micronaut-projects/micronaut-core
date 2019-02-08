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
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
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
}
