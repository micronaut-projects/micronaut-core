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
package io.micronaut.runtime.http.codec

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.io.buffer.ByteBufferFactory
import io.micronaut.http.MediaType
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class TextPlainCodecSpec extends Specification {

    @Shared TextPlainCodec codec = new TextPlainCodec(StandardCharsets.UTF_8)

    void "test the buffer min and max are correct for special characters"() {
        given:
        ByteBufferFactory bufferFactory = Mock(ByteBufferFactory)
        String val = 'é'

        when:
        codec.encode(val, bufferFactory)

        then:
        1 * bufferFactory.wrap(_) >> Stub(ByteBuffer)
    }

    void "test additional type configuration"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.codec.text.additionalTypes': ['text/html'],
                'micronaut.codec.json.additionalTypes': ['foo/javascript']
        ])

        when:
        TextPlainCodec codecBean = ctx.getBean(TextPlainCodec)

        then:
        codec.mediaTypes.size() == 1
        codecBean.mediaTypes.size() == 2
        codecBean.mediaTypes.contains(MediaType.of("text/html"))
        codecBean.mediaTypes.contains(MediaType.TEXT_PLAIN_TYPE)

        cleanup:
        ctx.close()
    }

    void "test additional type configuration 2"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.codec.json.additionalTypes': ['foo/javascript']
        ])

        when:
        TextPlainCodec codecBean = ctx.getBean(TextPlainCodec)

        then:
        codecBean.mediaTypes.size() == 1
        codecBean.mediaTypes.contains(MediaType.TEXT_PLAIN_TYPE)

        cleanup:
        ctx.close()
    }
}
