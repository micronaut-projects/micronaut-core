package io.micronaut.jackson.codec

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.http.codec.MediaTypeCodec

class JacksonMediaTypeCodecSpec extends AbstractTypeElementSpec {
    def 'no legacy media type codecs registered'() {
        given:
        def context = buildContext('', '', true)

        expect:
        // old codecs should be disabled
        !context.getBeansOfType(MediaTypeCodec).any { it instanceof JacksonMediaTypeCodec }
        // new codecs should be present
        context.getBeansOfType(MediaTypeCodec).any { it instanceof io.micronaut.json.codec.JsonMediaTypeCodec }
        context.getBeansOfType(MediaTypeCodec).any { it instanceof io.micronaut.json.codec.JsonStreamMediaTypeCodec }
        // old codecs can still be requested explicitly
        context.getBean(JsonMediaTypeCodec).class == JsonMediaTypeCodec
        context.getBean(JsonStreamMediaTypeCodec).class == JsonStreamMediaTypeCodec
    }
}
